# Project Context Summary

## Overview

This repository contains a Java-based RISC-V disassembler focused on the RV32I instruction set and ELF input files.
The project is structured like an academic pipeline, but the codebase is organized in a practical way so it can be built, tested, and demonstrated as a working application.

The project currently supports:

- Command-line disassembly of ELF files
- A JavaFX desktop UI
- ELF header parsing and full ELF loading
- Section and symbol resolution
- RV32I instruction decoding
- Multiple output modes: text assembly, JSON, and CFG-like output
- Sample binaries, assembly inputs, and helper scripts for WSL-based bare-metal tooling

## Primary Goal

The current implementation is centered on disassembling RV32I ELF binaries and presenting the decoded program in forms that are useful for learning, analysis, and demonstration:

- human-readable assembly
- machine-readable JSON
- a simple control-flow graph view

This makes the project fit both coursework/demo use and future extension work.

## Tech Stack

- Java 21
- Maven
- JavaFX 21.0.2
- JUnit 5 for tests

Build output is packaged into a shaded runnable JAR:

- `target/riscv-disassembler.jar`

## High-Level Architecture

The main execution flow is:

`CLI/UI -> DisassemblyRequest -> ELF Loader -> Section/Symbol Resolver -> RV32I Decoder -> IR -> Emitters/CFG`

Key modules:

- `Main`: process entry point
- `cli`: command-line parsing and execution
- `ui`: JavaFX application and launcher
- `pipeline`: shared orchestration layer used by both CLI and UI
- `elf`: ELF parsing and data models
- `resolver`: executable section and symbol resolution
- `decoder`: RV32I instruction decoding
- `ir`: intermediate representation for decoded instructions
- `emit`: output renderers
- `analysis`: CFG-related analysis classes

## Current Runtime Behavior

### CLI

The CLI entry point delegates to `DisassemblerCli`, which supports:

- `--input <file>`
- `--format <asm|json|cfg>`
- `--output <file>`
- `--header-only`
- `--disassemble-all`
- `--ui`
- `--debug`
- `--help`

Behavior notes:

- `--header-only` does a lenient header parse instead of full disassembly
- `--ui` launches the JavaFX app
- if `--output` is omitted, output is printed to stdout
- failures return non-zero exit codes and can include stack traces with `--debug`

### UI

The JavaFX UI exposes the same pipeline features as the CLI:

- choose input ELF
- choose output format
- optional output file
- header-only mode
- disassemble-all mode
- debug stack trace mode

The UI writes to file when requested and also shows the result in a text area.

## Pipeline Details

`DisassemblyPipeline` is the central coordinator. It currently does the following:

1. Load the ELF file or just the ELF header
2. Resolve sections and symbols into a `ResolvedProgram`
3. Decode instructions into `InstructionIr`
4. Emit the requested output format

Supported output backends:

- `TextEmitter`
- `JsonEmitter`
- `HeaderEmitter`
- `CfgBuilder`

## Repository Layout

Important paths:

- `src/main/java/org/hello/riscvdisassembler/`: application source
- `src/test/java/org/hello/riscvdisassembler/`: unit tests
- `samples/`: sample ELF/object/assembly files and generated outputs
- `scripts/`: helper scripts for WSL/Windows workflows
- `docs/`: project documents and reference material

## Scripts and Tooling Context

The repository includes helper scripts for working with bare-metal RV32I binaries under WSL.

Important files:

- `scripts/linker/rv32i-baremetal.ld`: default linker script
- `scripts/wsl/rv32i-baremetal-gcc.sh`: wrapper around a RISC-V GCC toolchain
- `scripts/wsl/rv32i-baremetal-build.sh`: wrapper that builds bare-metal RV32I and emits a `.map` file when linking
- `scripts/wsl/rv32i-baremetal-objdump.sh`
- `scripts/wsl/rv32i-baremetal-readelf.sh`
- `scripts/wsl/rv32i-baremetal-gdb.sh`
- `scripts/windows/open-kali-wsl-here.bat`

The build wrapper currently supports:

- compile-only output such as `.o`
- ELF linking with default `-march=rv32i` and `-mabi=ilp32`
- automatic use of the repo linker script when none is provided
- automatic `.map` output placed next to the requested output ELF

Example:

```bash
scripts/wsl/rv32i-baremetal-build.sh start.s main.c -o build/app.elf
```

Expected outputs:

- `build/app.elf`
- `build/app.map`

## Samples Context

The `samples/` folder contains:

- sample ELF files for disassembly
- example assembly inputs
- generated output examples
- map/object artifacts used while testing the bare-metal flow

One recent finding from the samples:

- `samples/code_and_data_seperate.s` uses `.section data` instead of `.section .data`
- the linker script only places `*(.data .data.*)` into the output `.data` section
- because of that mismatch, the input section named `data` is treated separately and the output `.data` section remains empty
- this is why `.data` and `.bss` can appear at the same address in `samples/code_and_data_seperate.map`: both sections are size `0`

In other words, the overlapping addresses there are not a linker bug; they come from empty output sections plus a section-name mismatch in the sample source.

## Testing Context

The test suite currently covers major subsystems, including:

- CLI behavior
- pipeline behavior
- ELF loading
- RV32I decoding
- emitters
- CFG building
- section/symbol resolution

Representative test classes:

- `DisassemblerCliTest`
- `DisassemblyPipelineTest`
- `ElfLoaderTest`
- `Rv32iDecoderTest`
- `EmittersTest`
- `CfgBuilderTest`
- `SectionSymbolResolverTest`

This suggests the codebase already has baseline regression coverage around the core pipeline.

## Build and Run

Build:

```bash
mvn clean package
```

Run from CLI:

```bash
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format asm
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format json
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format cfg
java -jar target/riscv-disassembler.jar --input samples/sample.elf --header-only
java -jar target/riscv-disassembler.jar --ui
```

## Current Scope and Limits

Based on the code and existing docs, the project is currently oriented toward:

- ELF32 little-endian input suitable for RV32I workflows
- decoding the RV32I base instruction set
- educational/static analysis use rather than full linker/loader emulation

Known future-direction items already reflected in the repo documentation:

- relocation support
- pseudo-instruction handling
- RV32M and RV32C support
- richer data analysis
- DOT/Graphviz export for CFG

## Practical Summary

At its current state, this repo is a working RV32I ELF disassembler with:

- a shared disassembly pipeline
- both CLI and JavaFX front ends
- tests across core modules
- WSL helper scripts for generating and inspecting bare-metal RISC-V binaries
- sample artifacts for validating decoding and linker behavior

It is already usable for demonstrations and coursework, while still being small enough to extend in targeted ways.
