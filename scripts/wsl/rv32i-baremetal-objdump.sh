#!/usr/bin/env bash
set -euo pipefail

tool="${RISCV_OBJDUMP:-}"

if [[ -z "$tool" ]]; then
  if command -v riscv64-unknown-elf-objdump >/dev/null 2>&1; then
    tool="riscv64-unknown-elf-objdump"
  elif command -v riscv64-linux-gnu-objdump >/dev/null 2>&1; then
    tool="riscv64-linux-gnu-objdump"
  else
    echo "[ERROR] Cannot find a RISC-V objdump tool in WSL." >&2
    exit 1
  fi
fi

if [[ $# -eq 0 ]]; then
  cat <<'EOF'
Usage:
  rv32i-baremetal-objdump <file> [objdump options]

Examples:
  rv32i-baremetal-objdump -d app.elf
  rv32i-baremetal-objdump -D -M no-aliases app.elf
  rv32i-baremetal-objdump -h app.elf
EOF
  exit 1
fi

exec "$tool" "$@"
