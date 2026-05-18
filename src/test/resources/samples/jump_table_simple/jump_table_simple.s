    .section .text
    .globl _start
_start:
    li a0, 2          # Index = 2
    
switch_func:
    # Bounds check
    li t0, 4          # Max cases
    bgeu a0, t0, default_case
    
    # Calculate address
    la t1, jump_table # Base address
    slli a0, a0, 2    # Index * 4
    add t1, t1, a0    # Base + Offset
    lw t2, 0(t1)      # Load target address
    jalr t2           # Jump!

case_0:
    li a1, 10
    j end
case_1:
    li a1, 20
    j end
case_2:
    li a1, 30
    j end
case_3:
    li a1, 40
    j end
default_case:
    li a1, 0
end:
    nop

    .section .rodata
    .align 2
jump_table:
    .word case_0
    .word case_1
    .word case_2
    .word case_3
    