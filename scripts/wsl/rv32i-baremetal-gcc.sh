#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
linker_script="${RISCV_LINKER_SCRIPT:-$script_dir/../linker/rv32i-baremetal.ld}"
toolchain="${RISCV_GCC:-}"

if [[ -z "$toolchain" ]]; then
  if command -v riscv64-unknown-elf-gcc >/dev/null 2>&1; then
    toolchain="riscv64-unknown-elf-gcc"
  elif command -v riscv64-linux-gnu-gcc >/dev/null 2>&1; then
    toolchain="riscv64-linux-gnu-gcc"
  else
    echo "[ERROR] Cannot find a RISC-V GCC toolchain in WSL." >&2
    echo "Install riscv64-unknown-elf-gcc or riscv64-linux-gnu-gcc first." >&2
    exit 1
  fi
fi

if [[ $# -eq 0 ]]; then
  cat <<'EOF'
Usage:
  rv32i-baremetal-gcc <files...> [gcc options]

Examples:
  rv32i-baremetal-gcc main.s -o main.elf
  rv32i-baremetal-gcc main.c -S -o main.s
  rv32i-baremetal-gcc main.c -c -o main.o
  rv32i-baremetal-gcc start.s main.c -o app.elf

Defaults injected by this wrapper:
  -march=rv32i
  -mabi=ilp32
  -nostdlib -nostartfiles when linking
  -T<rv32i-baremetal.ld> when linking and no custom -T is provided
EOF
  exit 1
fi

args=()
has_march=0
has_mabi=0
has_linker_script=0
compile_only=0
preprocess_only=0
emit_asm=0
shared_link=0

for arg in "$@"; do
  case "$arg" in
    -march=*) has_march=1 ;;
    -mabi=*) has_mabi=1 ;;
    -T|-Wl,-T*|-Wl,--script=*|-Wl,-T,*|-T*) has_linker_script=1 ;;
    -c) compile_only=1 ;;
    -S) emit_asm=1 ;;
    -E) preprocess_only=1 ;;
    -shared) shared_link=1 ;;
  esac
  args+=("$arg")
done

if (( has_march == 0 )); then
  args=(-march=rv32i "${args[@]}")
fi

if (( has_mabi == 0 )); then
  args=(-mabi=ilp32 "${args[@]}")
fi

needs_link=1
if (( compile_only == 1 || emit_asm == 1 || preprocess_only == 1 || shared_link == 1 )); then
  needs_link=0
fi

if (( needs_link == 1 )); then
  if [[ ! -f "$linker_script" ]]; then
    echo "[ERROR] Cannot find linker script: $linker_script" >&2
    exit 1
  fi
  if (( has_linker_script == 0 )); then
    args=(-T"$linker_script" "${args[@]}")
  fi
  args=(-nostdlib -nostartfiles "${args[@]}")
fi

exec "$toolchain" "${args[@]}"
