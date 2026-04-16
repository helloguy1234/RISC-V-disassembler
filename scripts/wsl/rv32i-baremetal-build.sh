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
  rv32i-baremetal-build.sh <files...> [gcc options]

Examples:
  rv32i-baremetal-build.sh main.c -o build/main.elf
  rv32i-baremetal-build.sh start.s main.c -o build/app.elf
  rv32i-baremetal-build.sh main.c -c -o build/main.o

Behavior:
  - Always injects -march=rv32i and -mabi=ilp32 unless already provided
  - In link mode, also injects:
      -nostdlib -nostartfiles
      -T<rv32i-baremetal.ld> when no custom linker script is provided
      -Wl,-Map=<output>.map
  - In compile-only mode (-c), no map file is emitted because the linker is not run
EOF
  exit 1
fi

args=()
has_march=0
has_mabi=0
has_linker_script=0
has_map_file=0
compile_only=0
preprocess_only=0
emit_asm=0
shared_link=0
output_path=""

expect_output_path=0
expect_linker_arg=0
for arg in "$@"; do
  if (( expect_output_path == 1 )); then
    output_path="$arg"
    expect_output_path=0
    args+=("$arg")
    continue
  fi

  if (( expect_linker_arg == 1 )); then
    if [[ "$arg" == -Map=* ]]; then
      has_map_file=1
    fi
    expect_linker_arg=0
    args+=("$arg")
    continue
  fi

  case "$arg" in
    -march=*) has_march=1 ;;
    -mabi=*) has_mabi=1 ;;
    -T|-Wl,-T*|-Wl,--script=*|-Wl,-T,*|-T*) has_linker_script=1 ;;
    -Wl,-Map=*|-Map=*) has_map_file=1 ;;
    -Xlinker) expect_linker_arg=1 ;;
    -c) compile_only=1 ;;
    -S) emit_asm=1 ;;
    -E) preprocess_only=1 ;;
    -shared) shared_link=1 ;;
    -o) expect_output_path=1 ;;
    -o*) output_path="${arg#-o}" ;;
  esac
  args+=("$arg")
done

if (( expect_output_path == 1 )); then
  echo "[ERROR] Missing output path after -o." >&2
  exit 1
fi

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

  if [[ -z "$output_path" ]]; then
    output_path="a.out"
  fi

  if (( has_map_file == 0 )); then
    output_dir=$(dirname -- "$output_path")
    output_name=$(basename -- "$output_path")
    output_stem="$output_name"

    if [[ "$output_name" == *.* && "$output_name" != .* ]]; then
      output_stem="${output_name%.*}"
    elif [[ "$output_name" == .*.* ]]; then
      output_stem="${output_name%.*}"
      if [[ -z "$output_stem" ]]; then
        output_stem="$output_name"
      fi
    fi

    if [[ "$output_dir" == "." ]]; then
      map_path="${output_stem}.map"
    else
      map_path="${output_dir}/${output_stem}.map"
    fi
  fi

  if (( has_linker_script == 0 )); then
    args=(-T"$linker_script" "${args[@]}")
  fi
  if (( has_map_file == 0 )); then
    args=(-nostdlib -nostartfiles "-Wl,-Map=${map_path}" "${args[@]}")
  else
    args=(-nostdlib -nostartfiles "${args[@]}")
  fi
fi

exec "$toolchain" "${args[@]}"
