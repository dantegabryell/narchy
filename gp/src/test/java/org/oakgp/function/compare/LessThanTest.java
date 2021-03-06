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
package org.oakgp.function.compare;

import org.junit.jupiter.api.Test;
import org.oakgp.function.AbstractFnTest;
import org.oakgp.function.Fn;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.oakgp.NodeType.integerType;
import static org.oakgp.NodeType.stringType;

public class LessThanTest extends AbstractFnTest {
    @Test
    public void assertSingletons() {
        assertSame(LessThan.create(integerType()), LessThan.create(integerType()));
        assertNotSame(LessThan.create(integerType()), LessThan.create(stringType()));
    }

    @Override
    protected LessThan getFunction() {
        return LessThan.create(integerType());
    }

    @Override
    public void testEvaluate() {
        evaluate("(< 7 8)").to(TRUE);
        evaluate("(< 8 8)").to(FALSE);
        evaluate("(< 9 8)").to(FALSE);

        evaluate("(< \"dog\" \"zebra\")").to(TRUE);
        evaluate("(< \"dog\" \"dog\")").to(FALSE);
        evaluate("(< \"dog\" \"apple\")").to(FALSE);
    }

    @Override
    public void testCanSimplify() {
        simplify("(< v0 v0)").to("false");
        simplify("(< 8 7)").to("false");
        simplify("(< 8 8)").to("false");
        simplify("(< 8 9)").to("true");
    }

    @Override
    public void testCannotSimplify() {
        cannotSimplify("(< v0 8)", integerType());
        cannotSimplify("(< 8 v0)", integerType());
        cannotSimplify("(< v0 v1)", integerType(), integerType());
    }

    @Override
    protected Fn[] getFunctionSet() {
        return new Fn[]{getFunction(), LessThan.create(stringType())};
    }
}
