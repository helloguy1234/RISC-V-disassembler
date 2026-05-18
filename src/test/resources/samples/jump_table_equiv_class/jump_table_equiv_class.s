    .section .text
    .globl _start
_start:
    li a0, 1          # Index = 1
    
switch_func_eq:
    # Bounds check on a0
    li t0, 3
    bgeu a0, t0, default_case_eq
    
    # Register move (Equivalence Class: a0 == a5)
    mv a5, a0
    
    # Unrelated instructions (noise)
    addi t0, t0, 1
    nop
    
    # Calculate address using a5 (instead of a0)
    la t1, jump_table_eq
    slli a5, a5, 2    
    add t1, t1, a5    
    lw t2, 0(t1)      
    jalr t2           

case_0_eq:
    li a1, 100
    j end_eq
case_1_eq:
    li a1, 200
    j end_eq
case_2_eq:
    li a1, 300
    j end_eq
default_case_eq:
    li a1, 0
end_eq:
    nop

    .section .rodata
    .align 2
jump_table_eq:
    .word case_0_eq
    .word case_1_eq
    .word case_2_eq