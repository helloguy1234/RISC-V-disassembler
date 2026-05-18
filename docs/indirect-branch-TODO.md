## Core Analysis & Discovery

_Các task phục hồi Indirect Branch dưới đây phải được thực hiện theo đúng thứ tự (Step 1 -> Step 4)._

- [x] 1 **[Step 1] IR & Semantic Lifting:** Nâng cấp cấu trúc `InstructionIr` thành hệ thống IR ngữ nghĩa bằng cách đính kèm cây AST (Abstract Syntax Tree) cho từng lệnh. Bước này chỉ mô hình hóa MỘT lệnh đơn lẻ, không liên kết/thế biến với các lệnh khác.
  - _Bản chất:_ Lệnh được mô hình hóa thành phép gán độc lập. Các biểu thức ghép lại thành cấu trúc cây: `BinaryOpExpr` là nút cành chứa 2 nút con, `RegisterExpr`/`ImmediateExpr` là nút lá kết thúc.
  - _Kiến trúc:_ Bổ sung vào package `org.hello.riscvdisassembler.core.decode.model`. Tạo interface `Expression` và các record con: `RegisterExpr` (chú ý là kể cả chỉ có 1 giá trị/toán hạng thì cũng là 1 biểu thức (ví dụ như 4 là biểu thức hằng, r1 là biểu thức bằng chính nó, ...)), `ImmediateExpr`, `BinaryOpExpr`, `MemoryLoadExpr` (biểu thức đọc dữ liệu từ bộ nhớ như lw r0, 0(r1) của riscv-32i), `UnknownExpr`, cùng với `AssignExpr(RegisterExpr lhs, Expression rhs)` thể hiện phép gán. Bổ sung thêm trường `AssignExpr semantic` vào record `InstructionIr`.
  - _Phạm vi thực hiện (Partial Lifting):_
    - Nhóm Address/ALU: `addi`, `add`, `slli`, `lui`, `auipc`.
    - Nhóm Memory Load: `lw`.
    - Nhóm Control Flow (để lấy ranh giới bounds): `bltu`, `bgeu`, `blt`, `bge`, `beq`, `bne`.
    - Fallback an toàn: Các lệnh chưa được mô hình hóa (như Store, XOR, ecall) sẽ trả về `UnknownExpr`.
- [x] 1 **[Step 2] Abstract Domain:** Xây dựng Miền trừu tượng cấu trúc (Abstract Domain D) để ghép nối các AST đơn lẻ ở Step 1 thành một phương trình toán học lớn (`TA = Base + Index * Scale`).
  - _Bản chất:_ Đây là một Môi trường Trạng thái (State Environment) của các thanh ghi lưu trữ dưới dạng Bảng băm (`Map<String, Expression>`), với Key là tên thanh ghi, Value là đối tượng (gốc cây AST) hiện tại mà thanh ghi đó đang nắm giữ.
  - _Thuật toán:_ Cấu trúc này mang tính chất tạm thời (transient). Khi gặp lệnh `jalr`, thuật toán (do Step 3 điều khiển) sẽ tạo một Map rỗng, duyệt xuôi theo CFG (đã được xây dựng trước đó bởi `CodeDiscoveryEngine`), dùng đệ quy thực hiện cơ chế Thế biến (Substitution) và Gộp trạng thái.
    - Hàm `substitute(Expression rhs, State state)`: Nhận vào vế phải (RHS) của lệnh hiện tại, duyệt đệ quy cây AST. Nếu gặp nút lá là `RegisterExpr` (VD: `x10`), tra cứu trong Map. Nếu Map đang lưu một gốc cây biểu thức cho `x10` (VD: `x1 + 4`), bứng nguyên nhánh cây `x1 + 4` đó thế vào vị trí của `x10`. Kết quả trả về là một cây AST lớn đã được thế biến hoàn chỉnh để lưu ngược lại vào Map cho thanh ghi đích (LHS).
    - Hàm `join(State A, State B)`: Dùng để gộp 2 Map trạng thái khi luồng điều khiển chập nhánh (VD: sau if/else). Quy tắc: So sánh từng thanh ghi, nếu cây AST ở State A giống State B thì giữ nguyên, nếu khác nhau thì gán thanh ghi đó thành `UnknownExpr` (đánh dấu mất giá trị).
  - _Kiến trúc:_ Tạo package mới `org.hello.riscvdisassembler.core.discover.indirect.domain` chứa class `State` (quản lý Map) và các hàm `substitute()`, `join()`.
- [x] 1 **[Step 3] Jump Table Analyzer:** Xây dựng module xử lý `jalr` bằng lý thuyết SJA (Structural Tracking, Bounds Analysis - Lan truyền ràng buộc, Fixpoint Iteration).
  - _Bản chất:_ Đây là bước "Giải phương trình". Nhận vào cây AST từ Step 2 và làm 3 việc:
    - (1) **Structural Pattern Matching**: Đối sánh mẫu CẤU TRÚC toán học của cây AST (chứ không phải mẫu lệnh assembly) xem có dạng `TA = MemoryLoad(Base + Index * Scale)` không để bóc tách các biến `Base`, `Index`, `Scale`.
    - (2) **Bounds Analysis**: Lần ngược (backward traversal) CFG từ lệnh `jalr` để tìm ranh giới của `Index`. **Điều kiện dừng duyệt lùi:** (A) _Chặn bằng mục tiêu (Early Exit)_: Dừng ngay khi tìm thấy lệnh rẽ nhánh là Bounds check (thỏa mãn Phụ thuộc dữ liệu: so sánh biến nằm cùng _Lớp tương đương_ với `Index` với một hằng số; VÀ Phụ thuộc luồng điều khiển: kiểm soát việc đi đến lệnh `jalr`). (B) _Chặn bằng ngưỡng (Threshold)_: Lùi tối đa một số block/lệnh cố định (VD: 5 blocks/50 lệnh), nếu vượt quá thì trả về Unknown để chống lặp vô hạn. Ràng buộc tìm được sẽ lan truyền trong _Lớp tương đương_ với độ phức tạp O(1) chứ không duyệt đệ quy AST. (_Lưu ý: "Lớp tương đương - Equivalence Class" ở đây là một cấu trúc dữ liệu tạm thời (như HashMap) để nhóm các thanh ghi có cùng giá trị trên đường đi lùi. Nó khởi tạo trống và dọn dẹp (GC) ngay sau khi giải xong._)
    - (3) **Target Resolution**: Chạy vòng lặp thay số vào `Index`, duyệt cây tính toán, và giả lập đọc bộ nhớ (.rodata) để lấy danh sách địa chỉ đích.
  - _Kiến trúc:_ Tạo class `IndirectBranchResolver` tại package `org.hello.riscvdisassembler.core.discover.indirect`. Chứa các hàm `extractStructure()`, `findBounds()`, và `resolveTargets()`. Trả về danh sách `List<Long>` (các địa chỉ nhánh thực sự).
- [x] 1 **[Step 4] Iterative Refinement:** Tích hợp bộ giải quyết vào luồng khám phá chính để giải quyết nghịch lý "con gà và quả trứng".
  - _Bản chất:_ Việc tích hợp này chính là vòng lặp kết nối Step 1, 2, 3 vào logic của chương trình chính. "Nghịch lý" ở đây là: `CodeDiscoveryEngine` cần đích nhảy để dựng CFG, nhưng `IndirectBranchResolver` lại cần CFG bán thành phẩm để đi lùi tìm ràng buộc. Cần cho chúng chạy đan xen nhau.
  - _Kiến trúc & Thuật toán:_ Sửa đổi vòng lặp `while (!worklist.isEmpty())` trong hàm `discoverRecursive` của class `CodeDiscoveryEngine`.
    - Khi decode ra một lệnh nhánh gián tiếp (VD: `jalr`), mặc định target của nó chưa biết, engine sẽ gán cho nhánh này trạng thái `UNRESOLVED` (tạm gác lại).
    - Truyền `DiscoveredProgram` (mang vai trò là đồ thị luồng điều khiển bán thành phẩm hiện tại) vào `IndirectBranchResolver.resolve(...)`.
    - Nhận lại danh sách địa chỉ đích (`List<Long> targets`).
    - Add tất cả các `targets` này vào lại `worklist` để engine coi chúng như những "điểm bắt đầu mới" (seeds) và tiếp tục quét tiếp mã ở các hàm/khối lệnh đó. Tiếp tục cho tới khi `worklist` rỗng (đạt hội tụ - Fixpoint).
