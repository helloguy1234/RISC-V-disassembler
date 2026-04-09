ENTRY(_start)

SECTIONS
{
  . = 0x80000000;

  .text : ALIGN(4)
  {
    KEEP(*(.init))
    KEEP(*(.text.entry))
    *(.text .text.*)
  }

  .rodata : ALIGN(4)
  {
    *(.rodata .rodata.*)
  }

  .data : ALIGN(4)
  {
    *(.data .data.*)
  }

  .bss : ALIGN(4)
  {
    *(.bss .bss.*)
    *(COMMON)
  }

  /DISCARD/ :
  {
    *(.comment)
    *(.note*)
    *(.eh_frame*)
  }
}
