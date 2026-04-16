.section .data
    bien1: .word 100
    bien2: .word 1, 2, 3, 4, 5, 6, 7, 8, 9, 0
.section .text
    .globl _start

_start:
    li a0, 5
    li a1, 7
    add a2, a0, a1

loop: 
    beq zero, zero, loop
    