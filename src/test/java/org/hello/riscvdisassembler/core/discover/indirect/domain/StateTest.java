package org.hello.riscvdisassembler.core.discover.indirect.domain;

import org.hello.riscvdisassembler.core.decode.model.ast.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateTest {

    @Test
    void testDeepSubstitution() {
        State state = new State();

        // Giả lập lệnh: addi t1, a0, 4 (t1 = a0 + 4)
        Expression a0Plus4 = new BinaryOpExpr(Operator.ADD, new RegisterExpr("a0"), new ImmediateExpr(4));
        state = state.update("t1", a0Plus4);

        // Giả lập lệnh tiếp theo: slli t2, t1, 1 (t2 = t1 * 2)
        Expression t1Times2 = new BinaryOpExpr(Operator.SHIFT_LEFT, new RegisterExpr("t1"), new ImmediateExpr(1));

        // Thực hiện thế biến cho vế phải của t2
        Expression substituted = State.substitute(t1Times2, state);

        // Kết quả mong đợi: t2 = (a0 + 4) << 1
        assertInstanceOf(BinaryOpExpr.class, substituted);
        BinaryOpExpr root = (BinaryOpExpr) substituted;
        assertEquals(Operator.SHIFT_LEFT, root.op());

        assertInstanceOf(BinaryOpExpr.class, root.left());
        BinaryOpExpr leftBranch = (BinaryOpExpr) root.left();
        assertEquals(Operator.ADD, leftBranch.op());
        assertEquals("a0", ((RegisterExpr) leftBranch.left()).name());
        assertEquals(4L, ((ImmediateExpr) leftBranch.right()).value());
    }

    @Test
    void testJoinSameState() {
        State a = new State().update("x1", new ImmediateExpr(10));
        State b = new State().update("x1", new ImmediateExpr(10));

        State joined = State.join(a, b);

        assertInstanceOf(ImmediateExpr.class, joined.get("x1"));
        assertEquals(10L, ((ImmediateExpr) joined.get("x1")).value());
    }

    @Test
    void testJoinDivergingState() {
        State a = new State().update("x1", new ImmediateExpr(10));
        State b = new State().update("x1", new ImmediateExpr(20));

        State joined = State.join(a, b);

        assertInstanceOf(UnknownExpr.class, joined.get("x1"));
    }

    @Test
    void testJoinMissingInOne() {
        State a = new State().update("x1", new ImmediateExpr(10));
        State b = new State();

        State joined = State.join(a, b);

        assertInstanceOf(UnknownExpr.class, joined.get("x1"));
    }
}