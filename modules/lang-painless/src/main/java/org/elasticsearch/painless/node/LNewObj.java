/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.Definition;
import org.elasticsearch.painless.Definition.Constructor;
import org.elasticsearch.painless.Definition.Struct;
import org.elasticsearch.painless.Definition.Type;
import org.elasticsearch.painless.Variables;
import org.elasticsearch.painless.MethodWriter;

import java.util.List;

/**
 * Respresents and object instantiation.
 */
public final class LNewObj extends ALink {

    final String type;
    final List<AExpression> arguments;

    Constructor constructor;

    public LNewObj(int line, int offset, String location, String type, List<AExpression> arguments) {
        super(line, offset, location, -1);

        this.type = type;
        this.arguments = arguments;
    }

    @Override
    ALink analyze(Variables variables) {
        if (before != null) {
            throw new IllegalArgumentException(error("Illegal new call with a target already defined."));
        } else if (store) {
            throw new IllegalArgumentException(error("Cannot assign a value to a new call."));
        }

        final Type type;

        try {
            type = Definition.getType(this.type);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(error("Not a type [" + this.type + "]."));
        }

        Struct struct = type.struct;
        constructor = struct.constructors.get(new Definition.MethodKey("new", arguments.size()));

        if (constructor != null) {
            Type[] types = new Type[constructor.arguments.size()];
            constructor.arguments.toArray(types);

            if (constructor.arguments.size() != arguments.size()) {
                throw new IllegalArgumentException(error("When calling constructor on type [" + struct.name + "]" +
                    " expected [" + constructor.arguments.size() + "] arguments, but found [" + arguments.size() + "]."));
            }

            for (int argument = 0; argument < arguments.size(); ++argument) {
                AExpression expression = arguments.get(argument);

                expression.expected = types[argument];
                expression.internal = true;
                expression.analyze(variables);
                arguments.set(argument, expression.cast(variables));
            }

            statement = true;
            after = type;
        } else {
            throw new IllegalArgumentException(error("Unknown new call on type [" + struct.name + "]."));
        }

        return this;
    }

    @Override
    void write(MethodWriter writer) {
        // Do nothing.
    }

    @Override
    void load(MethodWriter writer) {
        writer.writeDebugInfo(offset);
        writer.newInstance(after.type);

        if (load) {
            writer.dup();
        }

        for (AExpression argument : arguments) {
            argument.write(writer);
        }

        writer.invokeConstructor(constructor.owner.type, constructor.method);
    }

    @Override
    void store(MethodWriter writer) {
        throw new IllegalStateException(error("Illegal tree structure."));
    }
}
