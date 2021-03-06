/*
 * Copyright 2015 S. Webber
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.oakgp.function.classify;

import org.oakgp.Arguments;
import org.oakgp.Assignments;
import org.oakgp.function.Fn;
import org.oakgp.util.Signature;

import static org.oakgp.NodeType.booleanType;
import static org.oakgp.NodeType.integerType;

/**
 * Determines if a number is zero.
 */
public final class IsZero implements Fn {
    private static final Signature SIGNATURE = new Signature(booleanType(), integerType());

    @Override
    public Object evaluate(Arguments arguments, Assignments assignments) {
        int i = arguments.firstArg().eval(assignments);
        return i == 0;
    }

    @Override
    public Signature sig() {
        return SIGNATURE;
    }
}
