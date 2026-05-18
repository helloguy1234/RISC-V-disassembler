package org.hello.riscvdisassembler.core.discover.indirect.domain;

import org.hello.riscvdisassembler.core.decode.model.ast.BinaryOpExpr;
import org.hello.riscvdisassembler.core.decode.model.ast.Expression;
import org.hello.riscvdisassembler.core.decode.model.ast.ImmediateExpr;
import org.hello.riscvdisassembler.core.decode.model.ast.MemoryLoadExpr;
import org.hello.riscvdisassembler.core.decode.model.ast.RegisterExpr;
import org.hello.riscvdisassembler.core.decode.model.ast.UnknownExpr;

import java.util.HashMap;
import java.util.Map;

/**
 * Đại diện cho Miền trừu tượng D (Abstract Domain D) tại một điểm trong luồng
 * thực thi.
 * Quản lý trạng thái các thanh ghi dưới dạng phương trình toán học AST.
 * Class này được thiết kế bất biến (Immutable) để an toàn trong quá trình lặp
 * điểm cố định.
 */
public final class State {
    private final Map<String, Expression> environment;

    public State() {
        this.environment = Map.of();
    }

    private State(Map<String, Expression> environment) {
        this.environment = Map.copyOf(environment);
    }

    /**
     * Trả về một State mới với thanh ghi đã được cập nhật biểu thức AST mới.
     */
    public State update(String register, Expression expr) {
        Map<String, Expression> newEnv = new HashMap<>(this.environment);
        newEnv.put(register, expr);
        return new State(newEnv);
    }

    /**
     * Trích xuất biểu thức hiện tại của một thanh ghi (phục vụ cho Unit Test).
     */
    public Expression get(String register) {
        return environment.get(register);
    }

    /**
     * Gộp 2 trạng thái khi luồng điều khiển hội tụ.
     * Nếu giá trị của thanh ghi ở cả 2 nhánh giống nhau thì giữ nguyên, ngược lại
     * chuyển thành UnknownExpr.
     */
    public static State join(State a, State b) {
        Map<String, Expression> newEnv = new HashMap<>();

        for (Map.Entry<String, Expression> entry : a.environment.entrySet()) {
            String reg = entry.getKey();
            Expression exprA = entry.getValue();
            Expression exprB = b.environment.get(reg);

            if (exprB != null && exprA.equals(exprB)) {
                newEnv.put(reg, exprA);
            } else {
                newEnv.put(reg, new UnknownExpr());
            }
        }

        for (Map.Entry<String, Expression> entry : b.environment.entrySet()) {
            if (!a.environment.containsKey(entry.getKey())) {
                newEnv.put(entry.getKey(), new UnknownExpr());
            }
        }

        return new State(newEnv);
    }

    /**
     * Đệ quy duyệt cây AST (Vế phải - RHS) và thế các nút RegisterExpr bằng biểu
     * thức
     * đang được lưu trong State hiện tại.
     */
    public static Expression substitute(Expression rhs, State state) {
        if (rhs == null)
            return null;

        return switch (rhs) {
            case RegisterExpr reg -> {
                Expression sub = state.environment.get(reg.name());
                // Nếu có giá trị trong State thì lấy bứng nhánh đó đắp vào, nếu không thì giữ
                // nguyên tên thanh ghi
                yield sub != null ? sub : reg;
            }
            case ImmediateExpr imm -> imm;
            case UnknownExpr unk -> unk;
            case BinaryOpExpr(var op, var left, var right) -> new BinaryOpExpr(
                    op,
                    substitute(left, state),
                    substitute(right, state));
            case MemoryLoadExpr(var baseAddress, var sizeBytes) -> new MemoryLoadExpr(
                    substitute(baseAddress, state),
                    sizeBytes);
            default -> rhs;
        };
    }
}