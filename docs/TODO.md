# TODO / Future Work
## format
- [<completed/incomplete>] <priority> <description>
completed will be note witd v, incompleted is noted with x
priority start from 0 is highest priority

## Core Analysis & Discovery
- [ ] 2 Phục hồi indirect branch target (đặc biệt cho lệnh `jalr`).
- [ ] 1 Nhận diện Jump Table.
    với 2 cái trên hiện đang có các kỹ thuật sau đây
        Kỹ thuật 2: Nhận diện theo mẫu (Pattern Matching)Tính khả thi: Rất Cao (Nền tảng của dự án)Đánh giá: Sự đơn giản của RV32I khiến cho các compiler (như GCC, Clang) sinh ra các đoạn mã xử lý jump table rất dễ đoán. Bạn chỉ cần viết module tìm kiếm các mẫu tĩnh như: LUI (nạp địa chỉ cơ sở) $\rightarrow$ ADD (cộng chỉ số) $\rightarrow$ LW (tải địa chỉ từ bộ nhớ) $\rightarrow$ JALR (nhảy). Hoặc đối với mã PIC, mẫu sẽ là các cặp AUIPC + lệnh tải offset.Chiến lược bằng Java: Rất dễ dàng. Bạn có thể sử dụng biểu thức chính quy (Regex) trên chuỗi lệnh đã tháo gỡ hoặc thiết lập cơ chế "cửa sổ trượt" (sliding window) quét qua mảng Object đại diện cho các lệnh.Kỹ thuật 3: Phân tích luồng dữ liệu tĩnh / SJA (Static Analysis)Tính khả thi: Cao (Lợi thế tuyệt đối của Java)Đánh giá: Java với khả năng quản lý bộ nhớ tự động (Garbage Collection) và hỗ trợ OOP mạnh mẽ là ngôn ngữ hoàn hảo để bạn xây dựng Đồ thị Luồng Điều khiển (CFG) và cây Cú pháp Trừu tượng (AST). Các framework như LiSA (Library for Static Analysis) được viết bằng Java cung cấp sẵn các nền tảng diễn dịch trừu tượng rất mạnh.Chiến lược bằng Java: Hãy áp dụng thuật toán phân tích tiến cấu trúc (SJA). Khi gặp lệnh JALR rs1, bạn đi ngược lên (hoặc theo dõi từ đầu hàm) để giải phương trình toán học cấu thành nên thanh ghi rs1. Bạn có thể sử dụng thư viện JGraphT (một thư viện đồ thị cực mạnh của Java) để mô hình hóa CFG và lan truyền các giới hạn giá trị (bounds). Độ phức tạp thời gian $O(nm)$ của SJA là hoàn toàn tối ưu trên máy ảo JVM.
- [ ] 1 Phân tích hướng Relocation (relocation-aware analysis).
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
