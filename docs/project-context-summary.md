# Project Context Summary

## Overview

This repository is a Java 21 RISC-V disassembler centered on:

- RV32I instruction decoding
- ELF32 little-endian input
- static code discovery
- text / JSON / CFG outputs

The codebase is no longer organized as a flat set of utility packages. It has been refactored into a layered structure that reflects the actual pipeline:

- `entry`
- `app`
- `adapters`
- `core`
- `features`

This is important context, because the current architecture is no longer "ELF parser + decoder + emitters". It is now a staged pipeline with explicit contracts between phases.

## What The Program Does

The application reads an ELF file, converts it into an internal binary model, resolves executable regions and symbols, decides which addresses should actually be treated as code, decodes RV32I instructions, and renders the result in one of several formats.

The supported user-facing outputs are:

- assembly-like text
- JSON
- CFG summary
- ELF header-only output

It is best understood as a compact static disassembler for coursework, demonstrations, and further extension, not as a full reverse-engineering suite.

## Architecture

### 1. Entry Layer

The entry layer contains user-facing adapters:

- `entry.cli.DisassemblerCli`
- `entry.ui.UiLauncher`
- `entry.ui.JavaFxDisassemblerApp`

CLI and UI are not separate pipeline steps. They are two front ends over the same application pipeline.

### 2. Application Layer

The application layer is:

- `app.pipeline.DisassemblyRequest`
- `app.pipeline.DisassemblyPipeline`

`DisassemblyPipeline` is the orchestration boundary. It chooses:

- header-only flow vs full disassembly flow
- ELF loading
- adaptation into canonical binary model
- resolve
- discovery mode
- final output backend

### 3. Adapter Layer

The adapter layer currently contains ELF-specific input support:

- `adapters.input.elf.ElfLoader`
- `adapters.input.elf.ElfBinaryImageAdapter`
- `adapters.input.elf.model.*`

These classes are format-specific. They should be read as input adapters, not as the core domain model of the project.

### 4. Core Layer

The core processing pipeline is split into four subdomains.

#### `core.binary.model`

Canonical input model used by the core:

- `BinaryImage`
- `BinarySection`
- `BinarySymbol`

This is the normalized representation that separates the rest of the pipeline from ELF-specific DTOs.

#### `core.resolve`

Resolution phase:

- `SectionSymbolResolver`
- `ResolvedProgram`

This phase determines:

- which sections are executable
- how symbols are indexed
- what address ranges are valid for later discovery and decode

#### `core.discover`

Control-flow-aware discovery phase:

- `CodeDiscoveryEngine`
- `DiscoveryMode`
- `ControlFlowEdge`
- `DiscoveredProgram`
- `DiscoveredRegion`
- `RegionClassifier`
- `RegionKind`

This phase is now the central improvement over the original naive linear-sweep design.

It decides:

- which addresses should be decoded
- which direct targets should be followed
- what the retained instruction set is
- which ranges are code vs unreachable data gaps

#### `core.decode`

Instruction decoding phase:

- `InstructionDecoder`
- `Rv32iDecoder`
- `model.InstructionIr`

The decoder does not decide what is code. It only answers:

- what instruction exists at this address

That separation is deliberate and is one of the main architectural improvements in the repo.

### 5. Feature Layer

The downstream feature modules all consume `DiscoveredProgram`:

- `features.text.TextEmitter`
- `features.json.JsonEmitter`
- `features.cfg.CfgBuilder`
- `features.header.HeaderEmitter`

This means text, JSON, and CFG no longer operate on ad-hoc combinations of raw lists and resolver state. They all read the same canonical discovered result.

## Runtime Flow

### Full Disassembly

```text
DisassemblerCli / JavaFxDisassemblerApp
  -> DisassemblyRequest
  -> DisassemblyPipeline
  -> ElfLoader
  -> ElfBinaryImageAdapter
  -> BinaryImage
  -> SectionSymbolResolver
  -> ResolvedProgram
  -> CodeDiscoveryEngine
  -> DiscoveredProgram
  -> TextEmitter / JsonEmitter / CfgBuilder
```

### Header-Only

```text
DisassemblerCli / JavaFxDisassemblerApp
  -> DisassemblyRequest
  -> DisassemblyPipeline
  -> ElfLoader.loadHeader()
  -> HeaderEmitter
```

## Discovery Behavior

The project now supports two internal discovery modes.

### Recursive

This is the default mode for normal disassembly.

It:

- seeds from the entry point
- seeds from trusted executable symbols
- follows direct branch/jump/call targets
- keeps fall-through edges when valid
- avoids seeding weak local labels such as `$...` and `.` labels
- classifies unreachable gaps as discovered regions

This is the main mechanism used to reduce code/data confusion compared with the earlier linear-only design.

### Linear

This mode still exists for inspection-oriented behavior.

It:

- decodes all aligned words in selected executable sections
- still creates instruction-level edges
- is used when `--disassemble-all` is enabled

This preserves the previous "show me everything in the section" behavior while allowing the normal path to be more conservative.

## Current CLI Behavior

The CLI currently supports:

- `--input`, `-i`
- `--format`, `-f`
- `--output`, `-o`
- `--header-only`, `-H`
- `--disassemble-all`, `-a`
- `--ui`, `-u`
- `--debug`, `-d`
- `--help`, `-h`

Supported formats:

- `asm`
- `json`
- `cfg`

Important behavior:

- `--header-only` bypasses full disassembly and only parses the ELF header
- `--ui` launches the JavaFX app
- `--disassemble-all` currently forces linear discovery
- output is written to stdout unless `--output` is provided
- failures return non-zero exit codes

## UI Behavior

The JavaFX app uses the same `DisassemblyPipeline` as the CLI.

It currently supports:

- input selection
- output file selection
- format selection
- header-only mode
- disassemble-all mode
- debug stack trace mode

So the UI is not a separate implementation of the disassembler. It is another front end over the same use case layer.

## Output Semantics

### Text

`TextEmitter` renders:

- entry point metadata
- discovery mode
- section labels
- code instructions
- data regions as `.word` or `.byte` output where appropriate

### JSON

`JsonEmitter` serializes:

- discovery mode
- entry point
- executable sections
- symbols
- regions
- edges
- instructions

### CFG

`CfgBuilder` is no longer the place that rediscovers control flow.

It now:

- reads `DiscoveredProgram`
- uses discovered instruction-level edges
- groups instructions into basic blocks
- emits a CFG-oriented summary

This makes CFG a downstream feature rather than a second control-flow inference engine.

## Testing Status

The test suite covers the major subsystems:

- `ElfLoaderTest`
- `SectionSymbolResolverTest`
- `Rv32iDecoderTest`
- `CodeDiscoveryEngineTest`
- `EmittersTest`
- `CfgBuilderTest`
- `DisassemblyPipelineTest`
- `DisassemblerCliTest`

Current passing test count: `27`.

## Build And Run

Build:

```bash
mvn package
```

Run tests:

```bash
mvn test
```

Runnable jar:

```text
target/riscv-disassembler.jar
```

Example commands:

```bash
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format asm
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format json
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format cfg
java -jar target/riscv-disassembler.jar --input samples/sample.elf --header-only
java -jar target/riscv-disassembler.jar --ui
```

## Current Limits

The current implementation still has clear boundaries.

It does not yet provide:

- explicit user-facing discovery-mode selection
- indirect target recovery for `jalr`
- jump table recognition
- richer data classification such as `ALIGNMENT` or `UNKNOWN`
- relocation-aware reasoning
- pseudo-instruction lifting
- RV32M / RV32C support
- DOT/Graphviz CFG export

## Practical Interpretation

The correct way to understand the current codebase is:

- ELF is an input adapter concern
- `BinaryImage` is the canonical input contract
- `ResolvedProgram` is the canonical resolve-stage contract
- `DiscoveredProgram` is the canonical downstream disassembly contract
- text / JSON / CFG are sibling feature modules over the same discovered result

That is the most accurate mental model for the project in its current form.
