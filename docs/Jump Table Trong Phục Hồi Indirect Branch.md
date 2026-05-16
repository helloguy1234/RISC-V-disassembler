# Phục Hồi Phân Nhánh Gián Tiếp (Indirect Branch) Và Bảng Nhảy (Jump Table)

Trong phân tích mã nhị phân tĩnh (Static Binary Analysis), việc khôi phục Đồ thị Luồng Điều khiển (Control-Flow Graph - CFG) là tiền đề bắt buộc cho mọi tác vụ bảo mật như tìm kiếm lỗ hổng, dịch ngược (decompilation), hay cấy ghép Tính toàn vẹn Luồng điều khiển (CFI). Điểm nghẽn lớn nhất của quá trình này là các lệnh rẽ nhánh gián tiếp (indirect branches), nơi địa chỉ đích không được mã hóa cứng mà được tính toán động tại thời gian chạy (runtime). Việc phân giải thất bại các nhánh rẽ này sẽ làm đứt gãy đồ thị CFG, gây ra điểm mù cho hệ thống phân tích. Trong điều kiện hiện tại thì phạm vi nghiên cứu sẽ được thu hẹp lại chỉ xử lý với các tình huống của chương trình được biên dịch thông thường mà không bị làm rối.

## 1. Các tình huống sử dụng Phân nhánh Gián tiếp (Indirect Branch)

Trong các chương trình phần mềm chuẩn (đặc biệt là C/C++), rẽ nhánh gián tiếp được trình biên dịch sử dụng rất nhiều để triển khai các tính năng ngôn ngữ cấp cao. Ngoài switch-case (Jump Table), chúng bao gồm:

- Switch-Case

- Con trỏ hàm (Function Pointers): Rất phổ biến trong C để gọi các hàm callback, xử lý sự kiện, hoặc truyền hàm như một tham số.

- Gọi hàm đa hình (Virtual Function Calls): Trong lập trình hướng đối tượng (như C++), tính đa hình được triển khai bằng Bảng hàm ảo (vtable). Mỗi khi bạn gọi một hàm ảo của một đối tượng, trình biên dịch sinh ra các lệnh nạp địa chỉ hàm từ vtable và thực hiện nhảy gián tiếp.

- Tối ưu hóa gọi đuôi (Tail Call Optimization): Khi một hàm gọi một hàm khác ở ngay dòng cuối cùng trước khi trả về, trình biên dịch có thể loại bỏ lệnh gọi hàm (CALL / JAL) để tiết kiệm bộ nhớ Stack. Thay vào đó, nó dùng lệnh nhảy gián tiếp (JMP / JALR) để chuyển thẳng quyền điều khiển sang hàm đích.

- Xử lý ngoại lệ (Exception Handling): Cơ chế try-catch trong C++ thường yêu cầu rẽ nhánh gián tiếp. Khi có lỗi, luồng chương trình không đi theo tuần tự mà nhảy thông qua các con trỏ lấy từ bảng xử lý ngoại lệ (exception tables).

## 2. Bài Toán Phân Tích Indirect Branch

### 2.1. Cấu Trúc Của Một Bảng Nhảy (Jump Table)

Bảng nhảy là một mảng chứa các con trỏ địa chỉ hoặc các offset, thường được trình biên dịch sinh ra để tối ưu hóa cấu trúc điều khiển `switch-case`. Để phục hồi đích đến của một nhánh gián tiếp qua bảng nhảy, địa chỉ đích (Target Address - $TA$) thường được tính toán theo công thức:
$$TA = Base + (Index \times Scale)$$
Trong đó:

- **Base (Địa chỉ cơ sở):** Vị trí bắt đầu của bảng nhảy trong bộ nhớ.
- **Index (Biến chỉ số):** Biến trạng thái xác định nhánh rẽ (thường bị giới hạn bởi số lượng `case`).
- **Scale (Hệ số nhân):** Kích thước của mỗi phần tử trong bảng (ví dụ: 4 byte cho kiến trúc 32-bit).

### 2.2. Các Bài Toán Con Cần Giải Quyết

Để phục hồi chính xác một phân nhánh gián tiếp, bộ phân tích tĩnh phải vượt qua ba bài toán con tuần tự:

1. **Nhận diện và Theo dõi Biểu thức (Structural Tracking):**
   - _Mô tả:_ Hệ thống phải thiết lập được phương trình toán học tạo nên con trỏ đích nhằm bóc tách biến chỉ số ($Index$), địa chỉ cơ sở ($Base$), và hệ số nhân ($Scale$).
   - _Kỹ thuật giải quyết:_ Thay vì so khớp mẫu cú pháp (syntactic pattern-matching, ví dụ tìm kiếm chuỗi lệnh `lui` -> `addi` -> `slli` -> `add`) vốn dễ bị đánh lừa bởi mã tối ưu hóa (optimized code), kỹ thuật tiên tiến sử dụng **So khớp mẫu cấu trúc (Structural Pattern Matching)** trên **Miền trừu tượng Cấu trúc (Abstract Domain D)**. Kỹ thuật này duy trì các phép tính dưới dạng phương trình đại số qua AST (Abstract Syntax Tree), giúp bảo toàn mối quan hệ cấu trúc giữa các biến số mà không vội vàng định giá trị cụ thể [1]. Phương trình AST khổng lồ này được xây dựng thông qua kỹ thuật _Thế biến (Substitution)_, càn quét trên Đồ thị luồng điều khiển (CFG) bán thành phẩm (được engine khám phá tạo ra trước đó) khi hệ thống đụng độ lệnh phân nhánh gián tiếp.

2. **Phân tích Giới hạn (Bounds Analysis):**
   - _Mô tả:_ Xác định số lượng nhánh rẽ hợp lệ của bảng nhảy bằng cách nội suy cận trên và cận dưới của biến chỉ số thông qua các lệnh kiểm tra ranh giới (bounds checks) trong mã (ví dụ: lệnh so sánh `cmp` trước khi rẽ nhánh).
   - _Kỹ thuật giải quyết:_ Áp dụng **Lan truyền Ràng buộc Hai chiều (Bidirectional Constraint Propagation)** qua các lớp tương đương, tách biệt hoàn toàn với cây AST. Thuật toán đi lùi (backward traversal) từ lệnh gián tiếp để tìm một lệnh rẽ nhánh thỏa mãn 2 điều kiện: **Phụ thuộc dữ liệu (Data Dependency)** (lệnh so sánh một biến thuộc _Lớp tương đương_ của chỉ số Index với một hằng số) và **Phụ thuộc luồng điều khiển (Control Dependency)** (lệnh kiểm soát luồng thực thi đi tới lệnh phân nhánh gián tiếp). Khi tìm thấy, giới hạn đẳng thức và bất đẳng thức sẽ được lan truyền cho mọi biến có liên hệ đại số với nhau trong nhóm tương đương với độ phức tạp $O(1)$, đảm bảo vùng giới hạn được đánh giá chặt chẽ mà không vướng phải bùng nổ trạng thái.

3. **Phân giải Mục tiêu (Target Resolution):**
   - _Mô tả:_ Sau khi khoanh vùng được bảng nhảy, hệ thống mô phỏng thao tác đọc bộ nhớ để trích xuất các địa chỉ đích thực sự và gắn chúng vào CFG.
   - _Kỹ thuật giải quyết:_ Ứng dụng **Phân tích Tiến Toàn diện (Forward Analysis)** kết hợp **Lặp Điểm Cố định (Fixpoint Iteration)**.
     - _Phân tích tiến:_ Duyệt và đánh giá các biểu thức xuôi theo chiều luồng điều khiển, giúp duy trì toàn bộ ngữ cảnh trạng thái, tránh đánh mất thông tin của kỹ thuật cắt lát ngược (backward slicing) truyền thống.
     - _Lặp điểm cố định:_ Dựa trên nền tảng lý thuyết dàn (Lattice theory), điểm cố định là trạng thái $x$ thuộc dàn sao cho hàm chuyển đổi $f(x) = x$. Thuật toán cập nhật trạng thái bộ nhớ lặp đi lặp lại qua các khối mã trên CFG cho tới khi toàn bộ không gian trạng thái hội tụ. Cơ chế lặp (chaotic iteration) qua một danh sách công việc (worklist) bảo đảm việc tìm kiếm mục tiêu diễn ra trọn vẹn và an toàn (soundness).

### 2.3. Nghịch Lý "Con Gà Và Quả Trứng" Trong Phục Hồi CFG

Bài toán phục hồi CFG gặp phải một nghịch lý kinh điển:
- **Trình dựng CFG (Discovery Engine):** Cần biết lệnh gián tiếp (`jalr`) nhảy đi đâu thì mới có thể vẽ tiếp đồ thị.
- **Trình phân tích (Data-flow Analyzer):** Lại cần một đồ thị CFG để có thể đi lùi (backward) tìm lệnh kiểm tra ranh giới, từ đó mới tính ra được địa chỉ nhảy.

Các hệ thống hiện đại giải quyết nghịch lý này thông qua **Quá trình Phân tích Lặp (Iterative Refinement)** (hay còn gọi là Lặp điểm cố định - Fixpoint Iteration). Quá trình này đan xen việc dựng đồ thị và phân tích dữ liệu:
1. **Dựng cục bộ:** Hệ thống bắt đầu càn quét tuần tự và theo các nhánh trực tiếp (`jal`, `beq`...) để xây dựng một CFG bán thành phẩm.
2. **Tạm dừng:** Khi chạm trán lệnh phân nhánh gián tiếp (`jalr`), nó tạm dừng việc đi theo nhánh đó (do chưa có đích).
3. **Giải quyết:** Module phân tích (SJA) được gọi, nhận đầu vào chính là khối CFG bán thành phẩm vừa tạo. Nó lội ngược dòng CFG này, tìm ranh giới, tính toán phương trình AST và trả về danh sách các địa chỉ đích thực sự (ví dụ: 5 địa chỉ của bảng nhảy).
4. **Bơm hạt giống (Seed injection):** Các địa chỉ mới này được nhét ngược lại vào hàng đợi (worklist) của Trình dựng CFG.
5. **Tiếp tục:** Trình dựng CFG tiếp tục hoạt động từ các địa chỉ mới này, làm lộ ra các khối mã mới.
6. **Hội tụ:** Vòng lặp đan xen này cứ tiếp diễn cho đến khi hàng đợi công việc trống rỗng. Lúc này, CFG đạt "điểm cố định" (Fixpoint) - không còn nhánh rẽ nào mới được khám phá thêm.

### 2.4. Vai Trò Của Ngôn Ngữ Trung Gian (IR)

Mã máy thô thường phụ thuộc vào phần cứng và có ngữ nghĩa phức tạp. Việc nâng (lift) mã máy lên một Ngôn ngữ Trung gian (Intermediate Representation - IR) là bước bắt buộc để đơn giản hóa thuật toán phân tích. Tuy nhiên, rào cản lớn nhất là **Tính đúng đắn Dịch thuật (Translational Correctness)**.

Hệ thống phân tích tiên tiến sử dụng **Xác thực Dịch thuật Hình thức (Formal Translation Validation)**. Bằng cách định nghĩa ngữ nghĩa hình thức của tập lệnh (ISA) và IR, hệ thống chứng minh sự tương đương ở cấp độ từng lệnh vi mô (single-instruction validation). Quá trình này sinh ra các chứng chỉ (certificates) toán học và nạp vào hệ thống chứng minh định lý tự động nhằm đảm bảo quá trình nâng cấp mã không làm sai lệch tính chất của bảng nhảy [2].

## 3. Các Kỹ Thuật Phục Hồi Hiện Đại

### 3.1. Phân Tích Tập Giá Trị (Value Set Analysis - VSA)

**VSA là gì:** Phân tích Tập Giá trị là thuật toán phân tích tĩnh kinh điển dựa trên diễn dịch trừu tượng, giới thiệu bởi Balakrishnan và Reps [3]. VSA theo dõi đồng thời giá trị số nguyên và địa chỉ bằng cách duy trì một tập hợp các giá trị khả dĩ (value sets) mà các đối tượng dữ liệu có thể chứa tại mỗi điểm trong chương trình.

**Sự hạn chế khi dự đoán bộ nhớ:** VSA truyền thống thường gặp khó khăn với các nhánh gián tiếp do bản chất bảo thủ. Sự thất bại của VSA xuất phát từ 3 giới hạn:

1. **Bí danh con trỏ (Pointer Aliasing):** Việc xác định tĩnh nhiều con trỏ có cùng trỏ vào một ô nhớ qua nhiều luồng thực thi là vô cùng phức tạp.
2. **Cập nhật Yếu (Weak Updates):** Khi luồng điều khiển hội tụ, hệ thống không thể khẳng định chắc chắn ô nhớ bị ghi đè hoàn toàn (Strong Update) mà phải kết hợp giá trị cũ và mới (Weak Update), gây hiệu ứng "pha loãng" dữ liệu dẫn đến mất độ chính xác [4].
3. **Mất độ chính xác sớm (Over-approximation):** Để đảm bảo hội tụ trước các vòng lặp theo định lý Rice, VSA thường sử dụng phép mở rộng (widening) khiến các biến chỉ số bị đánh giá thành $\top$ (Top - giá trị bất kỳ) [1]. Khi chỉ số bảng nhảy đạt trạng thái $\top$, hệ thống không thể khoanh vùng được bảng nhảy, làm hỏng toàn bộ quá trình phục hồi CFG.

### 3.2. Phân Tích Bảng Nhảy Tĩnh (Static Jump Table Analysis - SJA)

Static Jump Table Analysis (SJA) là một hệ thống phân tích tĩnh tiên tiến được giới thiệu tại hội nghị ISSTA 2024, thiết kế chuyên biệt cho việc giải quyết các bảng nhảy (jump tables).
SJA không phụ thuộc vào việc tìm kiếm các mẫu cú pháp (pattern-matching) dễ bị lỗi của trình biên dịch. Thay vào đó, nó sử dụng một "Miền trừu tượng" (Abstract Domain D) (hay hiểu đơn giản là "cách mà hệ thống chọn để ghi nhớ và đại diện cho dữ liệu") mới để theo dõi "cấu trúc toán học" cấu thành nên con trỏ (ví dụ: phép nhân hệ số stride, cộng địa chỉ cơ sở). Nó kết hợp phương pháp phân tích tiến toàn diện (forward analysis) và lan truyền ràng buộc để tính toán kích thước bảng nhảy một cách an toàn.SJA là hệ thống phân tích tĩnh vượt qua rào cản của VSA thông qua hai cải tiến kiến trúc [1]:

- **Dùng Miền Trừu Tượng D (Abstract Domain D):** Thay vì vội vàng mở rộng giá trị thành $\top$, SJA dùng miền D để lưu cấu trúc con trỏ dưới dạng phương trình đại số. Việc trì hoãn định giá trị giúp bảo toàn liên kết giữa biến chỉ số và đích đến.
- **Lan Truyền Hai Chiều:** Lan truyền các bất đẳng thức ranh giới theo hai chiều (tiến và lùi) giúp SJA đánh giá chính xác giới hạn của bảng nhảy.

Khi đối mặt với dữ liệu I/O không thể giải tĩnh, SJA hy sinh một phần nhỏ độ chính xác để bảo toàn tính soundness: bắt đầu phép lặp điểm cố định với giả định $\top$ cho các nhánh không thể giải, thay vì chạy vô hạn.

### 3.2.1. Các khái niệm cốt lõi trong SJA (Static Jump Table Analysis) mà bạn cần biết

SJA là một thuật toán cực kỳ thanh lịch. Bạn chỉ cần nắm vững 4 khái niệm nền tảng sau :

Miền Trừu Tượng D (Abstract Domain D): Đây là cách SJA lưu trữ dữ liệu. Thay vì cố gắng tính toán và lưu một thanh ghi dưới dạng một con số (ví dụ: x = 5) hay một khoảng giá trị, miền D lưu trữ thanh ghi dưới dạng một phương trình đại số thể hiện cấu trúc tạo nên con trỏ (ví dụ: Target = Base + Index \* 4).

Lớp Tương Đương (Equivalence Classes): Là một thuật toán nhóm các biến hoặc thanh ghi có giá trị bằng nhau lại với nhau. Nếu thanh ghi a0 vừa được copy sang t1, chúng được đưa vào cùng một lớp tương đương.

Lan Truyền Ràng Buộc Hai Chiều (Bidirectional Constraint Propagation): Kỹ thuật cập nhật dữ liệu. Khi hệ thống tìm ra một điều kiện giới hạn của một biến, nó sẽ truyền giới hạn đó cho mọi biến khác nằm trong cùng Lớp tương đương, truyền đi theo cả hai hướng (từ trái qua phải và từ phải qua trái của phương trình).

Lặp Điểm Cố Định (Fixpoint Iteration): Đây là vòng lặp tổng của toàn bộ quá trình xây dựng Đồ thị Luồng điều khiển (CFG). Hệ thống liên tục lặp lại quá trình phân tích cho đến khi "đạt điểm cố định" – tức là không còn khám phá thêm được bất kỳ khối mã hay nhánh rẽ nào mới nữa.

### 3.2.2. Cách SJA giải quyết 3 bài toán con trong thực tế

Với một chương trình chuẩn, SJA giải quyết vấn đề lớn (phục hồi hoàn chỉnh CFG) bằng cách đi từ trên xuống dưới (Phân tích tiến) và áp dụng các khái niệm trên vào 3 bài toán con :

Bài toán 1: Theo dõi và xây dựng biểu thức
Khi đọc các lệnh assembly (ví dụ: các lệnh cộng, nhân, dịch bit dùng để tính địa chỉ mảng), SJA không vội vàng tính ra kết quả. Nó dùng Miền Trừu Tượng D để ghép các lệnh lại. Khi chạm tới lệnh phân nhánh gián tiếp (như JALR t1 trong RISC-V), nó sẽ nhìn vào phương trình của t1 và nhận diện được ngay cấu trúc chuẩn của bảng nhảy: Đích_nhảy = Đọc_bộ_nhớ(Địa_chỉ_cơ_sở + Biến_chỉ_số \* Hệ_số_nhân).

Bài toán 2: Phân tích giới hạn (Tìm số lượng nhánh rẽ)
Để biết bảng nhảy này phục vụ cho bao nhiêu trường hợp (cases), SJA quét ngược (Backward Traversal) dọc theo CFG một chút để tìm lệnh kiểm tra ranh giới thỏa mãn Phụ thuộc dữ liệu và Phụ thuộc luồng điều khiển (ví dụ lệnh so sánh BLTU a0, 5 đứng trước và quyết định việc có gọi lệnh JALR hay không). Sử dụng Lớp Tương Đương (Equivalence Classes) và Lan Truyền Hai Chiều (Constraint Propagation), nếu SJA biết Biến_chỉ_số có quan hệ bằng với a0, nó lập tức áp đặt giới hạn 0 ≤ Biến_chỉ_số < 5 vào bảng Lớp tương đương (không phải vào cây AST). Lớp tương đương này được tạo trên đường đi lùi và sẽ bị hủy bỏ sau khi phân giải xong nhánh gián tiếp đó.

**Cơ chế dừng an toàn của quá trình đi lùi (Backward Traversal):**
- **Chặn bằng mục tiêu (Early Exit):** Hệ thống dừng đi lùi ngay lập tức khi tìm được lệnh rẽ nhánh thỏa mãn cả 2 điều kiện: Phụ thuộc dữ liệu (so sánh một biến thuộc lớp tương đương của Index với một hằng số) và Phụ thuộc luồng điều khiển (kiểm soát việc luồng thực thi có đi tới JALR hay không).
- **Chặn bằng ngưỡng (Threshold/Block Limit):** Trình biên dịch thường đặt lệnh kiểm tra ranh giới ngay sát bảng nhảy. Để tránh lặp vô hạn hoặc lùi sang hàm khác (đối với mã tự viết/mã rối), thuật toán áp dụng một ngưỡng tối đa (VD: lùi tối đa 5 blocks hoặc 50 lệnh). Vượt quá ngưỡng này mà không tìm thấy mục tiêu, hệ thống trả về kết quả an toàn (Unknown) và từ chối phân giải để tránh sai lệch CFG.

**Lưu ý về sự đánh đổi (Trade-off) trong triển khai thực tế của Đồ án:**
Theo lý thuyết gốc của SJA (ISSTA '24), để đạt được tính toàn vẹn tuyệt đối (100% soundness), hệ thống phải sử dụng **Phân tích tiến (Forward Analysis)** toàn diện để truyền ranh giới từ trên xuống mà không cần ngưỡng dừng. Tuy nhiên, việc xây dựng một bộ máy giả lập (Abstract Execution Engine) chạy xuyên suốt từ đầu hàm đòi hỏi công sức thiết kế hạ tầng rất lớn.
Do đó, để đảm bảo tính khả thi và tiến độ nghiên cứu, đồ án áp dụng **thiết kế lai (Hybrid Approach)**: kết hợp cấu trúc lưu trữ tối ưu của SJA (AST, Miền D, Lớp tương đương) với cơ chế **Duyệt lùi giới hạn (Bounded Backward Traversal)** kế thừa từ các công cụ kinh điển như `angr` (với ngưỡng dừng 3-5 blocks). Sự đánh đổi này giúp giảm thiểu độ phức tạp lập trình, vẫn giải quyết chính xác phần lớn các bảng nhảy do trình biên dịch tiêu chuẩn sinh ra, nhưng chấp nhận việc bỏ qua phân giải (trả về `Unknown`) nếu ranh giới bị đặt ngoài ngưỡng tìm kiếm.

Bài toán 3: Phân giải mục tiêu (Trích xuất đích đến)
Bây giờ SJA đã có đủ dữ kiện. Nó sẽ tạo một vòng lặp thay các giá trị từ 0 đến 4 vào Biến_chỉ_số trong phương trình. Với mỗi giá trị, nó thực hiện một thao tác đọc bộ nhớ ảo tại phân vùng dữ liệu chỉ đọc (.rodata) của file nhị phân. Kết quả thu được là 5 địa chỉ bộ nhớ đích hợp lệ.
Cuối cùng, SJA vẽ 5 cạnh nối vào Đồ thị CFG, đánh dấu việc phá giải thành công bảng nhảy. Thông qua Lặp Điểm Cố Định, SJA sẽ tiếp tục đi theo 5 đường dẫn mới này để dịch ngược các đoạn code tiếp theo.

## Tài Liệu Tham Khảo

[1] H. Nguyen, S. Priyadarshan, and R. Sekar, "Scalable, Sound, and Accurate Jump Table Analysis," in _Proceedings of the 33rd ACM SIGSOFT International Symposium on Software Testing and Analysis (ISSTA '24)_, Vienna, Austria, Sep. 2024, pp. 541–552.

[2] J. C. K. et al., "Translational Correctness of Binary Lifting," _arXiv preprint arXiv:2404.04132v2_, 2024.

[3] G. Balakrishnan and T. W. Reps, "WYSINWYX: What You See Is Not What You eXecute," in _Verified Software: Theories, Tools, Experiments (VSTTE)_, 2005, pp. 202–213.

[4] P. Parizek, "Static Analysis: Pointers & Heap Structures," in _Lecture Notes for Program Analysis and Verification_, Charles University, Prague.

[5] M. Santra, B. Zhang, M. Lim, V. A. Dasu, D. Zeng, and G. Tan, "iResolveX: Multi-Layered Indirect Call Resolution via Static Reasoning and Learning-Augmented Refinement," _arXiv preprint arXiv:2601.17888_, Jan. 2026.
