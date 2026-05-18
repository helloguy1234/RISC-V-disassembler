    .section .text
    .globl _start
_start:
    li a0, 1          # Index = 1
    
switch_func_far:
    # Bounds check on a0
    li t0, 3
    bgeu a0, t0, default_case_far
    
    # 55 NOPs to exceed the 50 instruction threshold limit
    .rept 55
    nop
    .endr
    
    # Calculate address
    la t1, jump_table_far
    slli a0, a0, 2    
    add t1, t1, a0    
    lw t2, 0(t1)      
    jalr t2           

case_0_far:
    li a1, 11
    j end_far
case_1_far:
    li a1, 22
    j end_far
case_2_far:
    li a1, 33
    j end_far
default_case_far:
    li a1, 0
end_far:
    nop

    .section .rodata
    .align 2
jump_table_far:
    .word case_0_far
    .word case_1_far
    .word case_2_far