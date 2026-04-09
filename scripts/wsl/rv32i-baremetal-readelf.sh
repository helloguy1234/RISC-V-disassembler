#!/usr/bin/env bash
set -euo pipefail

tool="${RISCV_READELF:-}"

if [[ -z "$tool" ]]; then
  if command -v riscv64-unknown-elf-readelf >/dev/null 2>&1; then
    tool="riscv64-unknown-elf-readelf"
  elif command -v riscv64-linux-gnu-readelf >/dev/null 2>&1; then
    tool="riscv64-linux-gnu-readelf"
  else
    echo "[ERROR] Cannot find a RISC-V readelf tool in WSL." >&2
    exit 1
  fi
fi

if [[ $# -eq 0 ]]; then
  cat <<'EOF'
Usage:
  rv32i-baremetal-readelf <file> [readelf options]

Examples:
  rv32i-baremetal-readelf -h app.elf
  rv32i-baremetal-readelf -S app.elf
  rv32i-baremetal-readelf -s app.elf
EOF
  exit 1
fi

exec "$tool" "$@"
