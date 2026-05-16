# Phục Hồi Phân Nhánh Gián Tiếp (Indirect Branch) Và Bảng Nhảy (Jump Table)

Trong phân tích mã nhị phân tĩnh (Static Binary Analysis), việc khôi phục Đồ thị Luồng Điều khiển (Control-Flow Graph - CFG) là tiền đề bắt buộc cho mọi tác vụ bảo mật như tìm kiếm lỗ hổng, dịch ngược (decompilation), hay cấy ghép Tính toàn vẹn Luồng điều khiển (CFI). Điểm nghẽn lớn nhất của quá trình này là các lệnh rẽ nhánh gián tiếp (indirect branches), nơi địa chỉ đích không được mã hóa cứng mà được tính toán động tại thời gian chạy (runtime). Việc phân giải thất bại các nhánh rẽ này sẽ làm đứt gãy đồ thị CFG, gây ra điểm mù cho hệ thống phân tích.

## 1. Bài Toán Phân Tích Indirect Branch

### 1.1. Cấu Trúc Của Một Bảng Nhảy (Jump Table)
Bảng nhảy là một mảng chứa các con trỏ địa chỉ hoặc các offset, thường được trình biên dịch sinh ra để tối ưu hóa cấu trúc điều khiển `switch-case`. Để phục hồi đích đến của một nhánh gián tiếp qua bảng nhảy, địa chỉ đích (Target Address - $TA$) thường được tính toán theo công thức:
$$TA = Base + (Index \times Scale)$$
Trong đó:
- **Base (Địa chỉ cơ sở):** Vị trí bắt đầu của bảng nhảy trong bộ nhớ.
- **Index (Biến chỉ số):** Biến trạng thái xác định nhánh rẽ (thường bị giới hạn bởi số lượng `case`).
- **Scale (Hệ số nhân):** Kích thước của mỗi phần tử trong bảng (ví dụ: 4 byte cho kiến trúc 32-bit).

### 1.2. Các Bài Toán Con Cần Giải Quyết

Để phục hồi chính xác một phân nhánh gián tiếp, bộ phân tích tĩnh phải vượt qua ba bài toán con tuần tự:

1. **Nhận diện và Theo dõi Biểu thức (Structural Tracking):**  
   - *Mô tả:* Hệ thống phải thiết lập được phương trình toán học tạo nên con trỏ đích nhằm bóc tách biến chỉ số ($Index$), địa chỉ cơ sở ($Base$), và hệ số nhân ($Scale$).  
   - *Kỹ thuật giải quyết:* Thay vì so khớp mẫu cú pháp (pattern-matching) vốn dễ bị đánh lừa bởi mã tối ưu hóa (optimized code), kỹ thuật tiên tiến sử dụng **Miền trừu tượng Cấu trúc (Abstract Domain D)**. Kỹ thuật này duy trì các phép tính dưới dạng phương trình đại số qua AST (Abstract Syntax Tree), giúp bảo toàn mối quan hệ cấu trúc giữa các biến số mà không vội vàng định giá trị cụ thể [1].  

2. **Phân tích Giới hạn (Bounds Analysis):**  
   - *Mô tả:* Xác định số lượng nhánh rẽ hợp lệ của bảng nhảy bằng cách nội suy cận trên và cận dưới của biến chỉ số thông qua các lệnh kiểm tra ranh giới (bounds checks) trong mã (ví dụ: lệnh so sánh `cmp` trước khi rẽ nhánh).  
   - *Kỹ thuật giải quyết:* Áp dụng **Lan truyền Ràng buộc Hai chiều (Bidirectional Constraint Propagation)** qua các lớp tương đương. Khi phát hiện một phép so sánh, giới hạn đẳng thức và bất đẳng thức sẽ được lan truyền cả tiến và lùi cho mọi biến có liên hệ đại số với nhau, đảm bảo vùng giới hạn được đánh giá chặt chẽ [1].  

3. **Phân giải Mục tiêu (Target Resolution):**  
   - *Mô tả:* Sau khi khoanh vùng được bảng nhảy, hệ thống mô phỏng thao tác đọc bộ nhớ để trích xuất các địa chỉ đích thực sự và gắn chúng vào CFG.  
   - *Kỹ thuật giải quyết:* Ứng dụng **Phân tích Tiến Toàn diện (Forward Analysis)** kết hợp **Lặp Điểm Cố định (Fixpoint Iteration)**.  
     - *Phân tích tiến:* Duyệt và đánh giá các biểu thức xuôi theo chiều luồng điều khiển, giúp duy trì toàn bộ ngữ cảnh trạng thái, tránh đánh mất thông tin của kỹ thuật cắt lát ngược (backward slicing) truyền thống.  
     - *Lặp điểm cố định:* Dựa trên nền tảng lý thuyết dàn (Lattice theory), điểm cố định là trạng thái $x$ thuộc dàn sao cho hàm chuyển đổi $f(x) = x$. Thuật toán cập nhật trạng thái bộ nhớ lặp đi lặp lại qua các khối mã trên CFG cho tới khi toàn bộ không gian trạng thái hội tụ. Cơ chế lặp (chaotic iteration) qua một danh sách công việc (worklist) bảo đảm việc tìm kiếm mục tiêu diễn ra trọn vẹn và an toàn (soundness).

### 1.3. Nghịch Lý "Con Gà Và Quả Trứng" Trong Phục Hồi CFG

Phân tích luồng dữ liệu đòi hỏi phải có CFG, nhưng để xây dựng CFG lại cần phân tích luồng dữ liệu để giải quyết nhánh gián tiếp. Các hệ thống hiện đại giải quyết nghịch lý này thông qua **Quá trình Phân tích Lặp (Iterative Refinement)**. Hệ thống bắt đầu bằng việc xây dựng các khối CFG nội bộ (Intra-CFG) với nhánh trực tiếp. Khi chạm trán nhánh gián tiếp, module diễn dịch được gọi để phân tích biểu thức và ranh giới. Các mục tiêu mới tìm thấy sẽ được thêm vào hàng đợi, từ đó mở rộng CFG và làm lộ ra các vùng mã mới. Quá trình này lặp lại cho đến khi CFG đạt trạng thái hội tụ [1].

### 1.4. Vai Trò Của Ngôn Ngữ Trung Gian (IR)

Mã máy thô thường phụ thuộc vào phần cứng và có ngữ nghĩa phức tạp. Việc nâng (lift) mã máy lên một Ngôn ngữ Trung gian (Intermediate Representation - IR) là bước bắt buộc để đơn giản hóa thuật toán phân tích. Tuy nhiên, rào cản lớn nhất là **Tính đúng đắn Dịch thuật (Translational Correctness)**. 

Hệ thống phân tích tiên tiến sử dụng **Xác thực Dịch thuật Hình thức (Formal Translation Validation)**. Bằng cách định nghĩa ngữ nghĩa hình thức của tập lệnh (ISA) và IR, hệ thống chứng minh sự tương đương ở cấp độ từng lệnh vi mô (single-instruction validation). Quá trình này sinh ra các chứng chỉ (certificates) toán học và nạp vào hệ thống chứng minh định lý tự động nhằm đảm bảo quá trình nâng cấp mã không làm sai lệch tính chất của bảng nhảy [2].

## 2. Các Kỹ Thuật Phục Hồi Hiện Đại

### 2.1. Phân Tích Tập Giá Trị (Value Set Analysis - VSA)

**VSA là gì:** Phân tích Tập Giá trị là thuật toán phân tích tĩnh kinh điển dựa trên diễn dịch trừu tượng, giới thiệu bởi Balakrishnan và Reps [3]. VSA theo dõi đồng thời giá trị số nguyên và địa chỉ bằng cách duy trì một tập hợp các giá trị khả dĩ (value sets) mà các đối tượng dữ liệu có thể chứa tại mỗi điểm trong chương trình.

**Sự hạn chế khi dự đoán bộ nhớ:** VSA truyền thống thường gặp khó khăn với các nhánh gián tiếp do bản chất bảo thủ. Sự thất bại của VSA xuất phát từ 3 giới hạn:
1. **Bí danh con trỏ (Pointer Aliasing):** Việc xác định tĩnh nhiều con trỏ có cùng trỏ vào một ô nhớ qua nhiều luồng thực thi là vô cùng phức tạp.  
2. **Cập nhật Yếu (Weak Updates):** Khi luồng điều khiển hội tụ, hệ thống không thể khẳng định chắc chắn ô nhớ bị ghi đè hoàn toàn (Strong Update) mà phải kết hợp giá trị cũ và mới (Weak Update), gây hiệu ứng "pha loãng" dữ liệu dẫn đến mất độ chính xác [4].  
3. **Mất độ chính xác sớm (Over-approximation):** Để đảm bảo hội tụ trước các vòng lặp theo định lý Rice, VSA thường sử dụng phép mở rộng (widening) khiến các biến chỉ số bị đánh giá thành $\top$ (Top - giá trị bất kỳ) [1]. Khi chỉ số bảng nhảy đạt trạng thái $\top$, hệ thống không thể khoanh vùng được bảng nhảy, làm hỏng toàn bộ quá trình phục hồi CFG.

### 2.2. Phân Tích Bảng Nhảy Tĩnh (Static Jump Table Analysis - SJA)

SJA là hệ thống phân tích tĩnh vượt qua rào cản của VSA thông qua hai cải tiến kiến trúc [1]:
- **Dùng Miền Trừu Tượng D (Abstract Domain D):** Thay vì vội vàng mở rộng giá trị thành $\top$, SJA dùng miền D để lưu cấu trúc con trỏ dưới dạng phương trình đại số. Việc trì hoãn định giá trị giúp bảo toàn liên kết giữa biến chỉ số và đích đến.  
- **Lan Truyền Hai Chiều:** Lan truyền các bất đẳng thức ranh giới theo hai chiều (tiến và lùi) giúp SJA đánh giá chính xác giới hạn của bảng nhảy. 

Khi đối mặt với dữ liệu I/O không thể giải tĩnh, SJA hy sinh một phần nhỏ độ chính xác để bảo toàn tính soundness: bắt đầu phép lặp điểm cố định với giả định $\top$ cho các nhánh không thể giải, thay vì chạy vô hạn.


## Tài Liệu Tham Khảo

[1] H. Nguyen, S. Priyadarshan, and R. Sekar, "Scalable, Sound, and Accurate Jump Table Analysis," in *Proceedings of the 33rd ACM SIGSOFT International Symposium on Software Testing and Analysis (ISSTA '24)*, Vienna, Austria, Sep. 2024, pp. 541–552.

[2] J. C. K. et al., "Translational Correctness of Binary Lifting," *arXiv preprint arXiv:2404.04132v2*, 2024.

[3] G. Balakrishnan and T. W. Reps, "WYSINWYX: What You See Is Not What You eXecute," in *Verified Software: Theories, Tools, Experiments (VSTTE)*, 2005, pp. 202–213.

[4] P. Parizek, "Static Analysis: Pointers & Heap Structures," in *Lecture Notes for Program Analysis and Verification*, Charles University, Prague.

[5] M. Santra, B. Zhang, M. Lim, V. A. Dasu, D. Zeng, and G. Tan, "iResolveX: Multi-Layered Indirect Call Resolution via Static Reasoning and Learning-Augmented Refinement," *arXiv preprint arXiv:2601.17888*, Jan. 2026.