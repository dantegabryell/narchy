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
package org.oakgp.examples.ant;

import org.junit.jupiter.api.Test;
import org.oakgp.Arguments;
import org.oakgp.node.FnNode;
import org.oakgp.node.Node;
import org.oakgp.node.VariableNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.oakgp.examples.ant.AntMovement.*;
import static org.oakgp.function.sequence.BiSequence.BISEQUENCE;
import static org.oakgp.function.sequence.TriSequence.TRISEQUENCE;
import static org.oakgp.util.Void.VOID_CONSTANT;

public class BiSequenceTest {
    private final Node stateVariable = new VariableNode(0, MutableState.STATE_TYPE);
    private final Node forward = new FnNode(FORWARD, stateVariable);
    private final Node left = new FnNode(LEFT, stateVariable);
    private final Node right = new FnNode(RIGHT, stateVariable);

    @Test
    public void testSimplifyWhenLeftAndRight() {
        assertEquals(VOID_CONSTANT, simplify(left, right));
    }

    @Test
    public void testSimplifyWhenRightAndLeft() {
        assertEquals(VOID_CONSTANT, simplify(right, left));
    }

    @Test
    public void testFirstArgBi() {
        Node firstArg = new FnNode(BISEQUENCE, left, forward);
        Node secondArg = right;
        Node expected = new FnNode(TRISEQUENCE, left, forward, secondArg);
        assertEquals(expected, simplify(firstArg, secondArg));
    }

    @Test
    public void testSecondArgBi() {
        Node firstArg = right;
        Node secondArg = new FnNode(BISEQUENCE, forward, left);
        Node expected = new FnNode(TRISEQUENCE, firstArg, forward, left);
        assertEquals(expected, simplify(firstArg, secondArg));
    }

    @Test
    public void testBothArgsBi() {
        Node firstArg = new FnNode(BISEQUENCE, left, forward);
        Node secondArg = new FnNode(BISEQUENCE, forward, right);
        Node expected = new FnNode(TRISEQUENCE, left, forward, secondArg);
        assertEquals(expected, simplify(firstArg, secondArg));
    }

    @Test
    public void testCannotSimplify() {
        assertCannotSimplify(left, left);
        assertCannotSimplify(right, right);
        assertCannotSimplify(left, forward);
        assertCannotSimplify(forward, right);
        assertCannotSimplify(forward, left);
        assertCannotSimplify(right, forward);
        assertCannotSimplify(forward, forward);
    }

    private void assertCannotSimplify(Node first, Node second) {
        assertNull(simplify(first, second));
    }

    private Node simplify(Node first, Node second) {
        return BISEQUENCE.simplify(new Arguments(new Node[]{first, second}));
    }
}
