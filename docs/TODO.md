# TODO / Future Work

## format

- [<completed/incomplete>] <priority> <description>
  completed will be note witd v, incompleted is noted with x
  priority start from 0 is highest priority

## Core Analysis & Discovery

_Các task phục hồi Indirect Branch dưới đây phải được thực hiện theo đúng thứ tự (Step 1 -> Step 4)._

- [ ] 1 **[Step 1] IR & Semantic Lifting:** Nâng cấp cấu trúc `InstructionIr` thành hệ thống IR ngữ nghĩa (mô hình hóa phép gán, phép tính đại số).
  - _Kiến trúc:_ Bổ sung vào package `org.hello.riscvdisassembler.core.decode.model`. Xây dựng cây cú pháp trừu tượng (AST) nội bộ với interface `Expression` và các record con: `RegisterExpr`, `ImmediateExpr`, `BinaryOpExpr`, `MemoryLoadExpr`, `UnknownExpr`.
  - _Phạm vi thực hiện (Partial Lifting):_
    - Nhóm Address/ALU: `addi`, `add`, `slli`, `lui`, `auipc`.
    - Nhóm Memory Load: `lw`.
    - Nhóm Control Flow (để lấy ranh giới bounds): `bltu`, `bgeu`, `blt`, `bge`, `beq`, `bne`.
    - Fallback an toàn: Các lệnh chưa được mô hình hóa (như Store, XOR, ecall) sẽ trả về `UnknownExpr`.
- [ ] 1 **[Step 2] Abstract Domain:** Xây dựng Miền trừu tượng cấu trúc (Abstract Domain D) theo dõi giá trị thanh ghi dưới dạng phương trình toán học (`TA = Base + Index * Scale`).
  - _Kiến trúc:_ Tạo package mới `org.hello.riscvdisassembler.core.discover.indirect.domain` chứa các cấu trúc dữ liệu theo dõi trạng thái.
- [ ] 1 **[Step 3] Jump Table Analyzer:** Xây dựng module xử lý `jalr` bằng lý thuyết SJA (Structural Tracking, Bounds Analysis, Fixpoint Iteration).
  - _Kiến trúc:_ Tạo class `IndirectBranchResolver` tại package `org.hello.riscvdisassembler.core.discover.indirect`. Class này hoàn toàn độc lập, chỉ nhận input là CFG nội bộ và trạng thái lệnh.
- [ ] 1 **[Step 4] Iterative Refinement:** Tích hợp bộ giải quyết vào luồng khám phá chính để giải quyết nghịch lý "con gà và quả trứng".
  - _Kiến trúc:_ Sửa đổi trực tiếp hàm `discoverRecursive` trong `org.hello.riscvdisassembler.core.discover.CodeDiscoveryEngine`. Khi gặp nhánh gián tiếp có `target == null`, gọi `IndirectBranchResolver` để thu thập list target và add vào `worklist`.
- [ ] 2 Phân tích hướng Relocation (relocation-aware analysis).
- [ ] 5 Cải thiện phân loại data vùng nhớ (thêm heuristics cho `ALIGNMENT` và vùng `UNKNOWN`).

## Decoding

- [ ] 6 Hỗ trợ dịch Pseudo-instruction (pseudo-instruction lifting).
- [ ] 6 Hỗ trợ tập lệnh RV32M (Multiply Extension).
- [ ] 6 Hỗ trợ tập lệnh RV32C (Compressed Instructions).

## Features & CLI

- [ ] 5 thêm option rút gọn cho CLI
- [ ] 4 Thêm tùy chọn trên CLI/UI để người dùng chọn Explicit Discovery Mode (ví dụ: Linear hoặc Recursive).
- [ ] 7 Hỗ trợ xuất đồ thị luồng điều khiển (CFG) dưới định dạng DOT/Graphviz.

## Testing

- [ ] 0 Bổ sung Unit Test toàn diện cho `Rv32iDecoder` (bao phủ tất cả định dạng lệnh: R, I, S, B, U, J và opcode không hợp lệ).
- [ ] 1 Bổ sung Unit Test cho `CodeDiscoveryEngine` với nhiều luồng điều khiển và các khối code phức tạp.
- [ ] 2 Viết thêm Integration/E2E Test với file ELF thực tế (VD: file chứa mã xen lẫn dữ liệu, file lỗi header) để đảm bảo độ ổn định của hệ thống.
