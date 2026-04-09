# Scripts Layout

The scripts folder is organized by execution environment and purpose:

```text
scripts/
|-- linker/
|   `-- rv32i-baremetal.ld
|-- windows/
|   `-- open-kali-wsl-here.bat
`-- wsl/
    |-- rv32i-baremetal-gcc
    |-- rv32i-baremetal-objdump
    |-- rv32i-baremetal-readelf
    `-- rv32i-baremetal-gdb
```

Recommended entry points:

- `scripts/windows/open-kali-wsl-here.bat`: open Kali WSL in the current project directory.
- `scripts/wsl/rv32i-baremetal-gcc`: GCC-like WSL wrapper for bare-metal RV32I builds.
- `scripts/wsl/rv32i-baremetal-objdump`: WSL wrapper for `objdump`.
- `scripts/wsl/rv32i-baremetal-readelf`: WSL wrapper for `readelf`.
- `scripts/wsl/rv32i-baremetal-gdb`: WSL wrapper for `gdb`.
- `scripts/linker/rv32i-baremetal.ld`: default bare-metal linker script.

Quick examples:

- Open Kali from Windows:
  `scripts\windows\open-kali-wsl-here.bat`
- Build ELF inside WSL:
  `scripts/wsl/rv32i-baremetal-gcc samples/test.s -o samples/build/test.elf`
- Disassemble inside WSL:
  `scripts/wsl/rv32i-baremetal-objdump -D samples/build/test.elf`
- Inspect ELF sections inside WSL:
  `scripts/wsl/rv32i-baremetal-readelf -S samples/build/test.elf`
- Start GDB inside WSL:
  `scripts/wsl/rv32i-baremetal-gdb samples/build/test.elf`

Notes:

- Toolchain wrappers are intentionally kept in WSL only.
- Use Windows only to open the WSL terminal, then run build/debug commands inside WSL.
