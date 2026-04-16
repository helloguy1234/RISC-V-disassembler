.section .text
    .globl _start

_start:
    # 1. Các lệnh khởi động bình thường
    li a0, 5
    li a1, 7
    add a2, a0, a1

    # 2. Lệnh nhảy sinh tử (Bảo vệ CPU)
    # CPU phải nhảy qua phần dữ liệu bên dưới, nếu không nó sẽ tưởng 
    # dữ liệu là mã lệnh và chạy thẳng vào, gây lỗi sập hệ thống (Illegal Instruction).
    j continue_code

# =========================================================
# BẮT ĐẦU VÙNG DỮ LIỆU NHÚNG TRỰC TIẾP TRONG .TEXT
# =========================================================
inline_data:
    .word 0xDEADBEEF        # Bẫy số 1: Hằng số nguyên 32-bit (Nhìn giống mã máy nhưng không phải)
    
    .string "Hack"          # Bẫy số 2: Chuỗi ký tự (H, a, c, k, \0 -> Tổng cộng 5 bytes)
    
    .align 2                # BƯỚC CỨU MẠNG: Ép Trình biên dịch chèn thêm byte đệm (padding)
                            # để lệnh tiếp theo bắt buộc phải nằm ở địa chỉ chia hết cho 4.
                            # (2^2 = 4 bytes). Nếu thiếu dòng này, các lệnh bên dưới sẽ bị lệch bit.
# =========================================================
# KẾT THÚC VÙNG DỮ LIỆU
# =========================================================

continue_code:
    # 3. Code tiếp tục chạy an toàn
    la t0, inline_data      # Lấy địa chỉ của vùng dữ liệu nhúng ở trên
    lw t1, 0(t0)            # Đọc con số 0xDEADBEEF vào thanh ghi t1
    
    # 4. Lặp vô hạn để kết thúc chương trình
hang:
    j hang