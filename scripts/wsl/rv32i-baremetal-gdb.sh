#!/usr/bin/env bash
set -euo pipefail

tool="${RISCV_GDB:-}"

if [[ -z "$tool" ]]; then
  if command -v riscv64-unknown-elf-gdb >/dev/null 2>&1; then
    tool="riscv64-unknown-elf-gdb"
  elif command -v riscv64-linux-gnu-gdb >/dev/null 2>&1; then
    tool="riscv64-linux-gnu-gdb"
  else
    echo "[ERROR] Cannot find a RISC-V gdb tool in WSL." >&2
    exit 1
  fi
fi

if [[ $# -eq 0 ]]; then
  cat <<'EOF'
Usage:
  rv32i-baremetal-gdb <file> [gdb options]

Examples:
  rv32i-baremetal-gdb app.elf
  rv32i-baremetal-gdb app.elf -ex "target remote :1234"
EOF
  exit 1
fi

exec "$tool" "$@"
