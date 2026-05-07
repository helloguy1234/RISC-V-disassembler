# Discovery Module

## Purpose

`core.discover` is the stage that sits between:

- `core.resolve`
- `core.decode`
- downstream output features

Its job is not to decode bits. Its job is to decide:

- which addresses should be treated as code
- where traversal should continue
- which direct control-flow edges exist
- which ranges are retained as code or classified as unreachable data gaps

This is the architectural answer to the original linear-sweep limitation of the project.

## Why It Exists

The old naive approach decoded every aligned word in executable sections.

That causes the classic problem:

- embedded data in `.text` can be decoded as instructions
- jump-over data regions can be mistaken for reachable code
- CFG quality degrades because data can become fake blocks

`CodeDiscoveryEngine` exists to move the project from:

- section-wide linear decoding

to:

- traversal-guided disassembly

## Position In The Pipeline

Normal flow:

```text
ElfLoader
  -> ElfBinaryImageAdapter
  -> BinaryImage
  -> SectionSymbolResolver
  -> ResolvedProgram
  -> CodeDiscoveryEngine
  -> DiscoveredProgram
  -> TextEmitter / JsonEmitter / CfgBuilder
```

This means discovery is now the gatekeeper for downstream outputs.

## Core Types

### `CodeDiscoveryEngine`

Main orchestration service for discovery.

Responsibilities:

- choose discovery strategy
- collect initial seeds
- traverse reachable addresses
- record control-flow edges
- build `DiscoveredProgram`

### `DiscoveryMode`

Current supported modes:

- `RECURSIVE`
- `LINEAR`

### `DiscoveredProgram`

Canonical result of the discovery stage.

Contains:

- `ResolvedProgram`
- retained `InstructionIr` list
- `ControlFlowEdge` list
- `DiscoveredRegion` list
- active `DiscoveryMode`

Downstream modules now use this object directly.

### `ControlFlowEdge`

Represents an instruction-level direct edge between two discovered addresses.

These edges are later consumed by:

- JSON output
- CFG construction

### `DiscoveredRegion`

Represents a contiguous discovered range inside a section.

Currently used to distinguish:

- code
- data gaps

### `RegionKind`

Current region categories are still simple.

The implementation is intentionally incremental and does not yet expose a richer alignment/unknown taxonomy.

### `RegionClassifier`

Classifies ranges after instruction retention has been decided.

At the moment it mainly identifies unreachable gaps inside executable sections so text and JSON output can render them explicitly.

## Recursive Mode

`RECURSIVE` is the default mode for normal disassembly.

The current algorithm is:

1. collect trusted seeds
2. decode one instruction at a seed
3. inspect its direct control-flow behavior
4. enqueue valid successors
5. stop at returns / terminators / invalid continuation boundaries

Current successor rules:

- `NORMAL`
  - follow fall-through
- `CONDITIONAL_BRANCH`
  - follow fall-through
  - enqueue direct branch target
- `UNCONDITIONAL_JUMP`
  - enqueue direct target
- `CALL`
  - enqueue direct target when it stays in a known section
  - continue with fall-through
- `RETURN`
  - stop current path
- `TERMINATOR`
  - stop current path

## Seed Policy

The current implementation is deliberately conservative compared with the earlier version.

It trusts:

- entry point
- section start
- symbols that look like real code seeds
  - explicit function symbols
  - exported code labels

It avoids seeding labels that are likely noise or data markers, such as:

- names starting with `$`
- names starting with `.`

This is why embedded labels like `$d` no longer automatically turn into separate recursive code paths.

## Linear Mode

`LINEAR` is still kept for inspection mode.

It:

- decodes every aligned instruction-sized word in selected executable sections
- records sequential edges
- records direct branch targets when available
- preserves the previous "show everything" behavior

This mode is currently selected by the pipeline when:

- `--disassemble-all` is enabled

So the project keeps compatibility with full-section inspection while using recursive discovery by default.

## Separation From Decoder

This separation is fundamental.

The decoder answers:

```text
What instruction is stored at this address?
```

The discovery engine answers:

```text
Should this address be decoded at all, and what addresses come next?
```

Without this split, the project would keep conflating:

- bit-level decode
- control-flow traversal
- code/data selection

The current design fixes that.

## Separation From CFG

`CfgBuilder` is no longer the place where control flow is rediscovered.

Discovery now owns:

- instruction retention
- direct edges

CFG now mainly does:

- grouping discovered instructions into blocks
- consuming discovered edges
- emitting block-oriented graph summaries

That makes CFG a downstream feature module instead of a second analysis engine trying to infer flow independently.

## Downstream Impact

Because `TextEmitter`, `JsonEmitter`, and `CfgBuilder` all consume `DiscoveredProgram`, they now share one consistent view of:

- which instructions exist
- which edges exist
- which regions are code vs data
- which mode produced the result

This is the main architectural payoff of the discovery layer.

## Current Limits

The module is improved, but still intentionally lightweight.

It does not yet implement:

- indirect target recovery for `jalr`
- jump table recognition
- richer edge kinds
- aggressive heuristics for mixed code/data separation
- explicit `ALIGNMENT` or `UNKNOWN` region rendering
- user-facing discovery mode selection in CLI/UI

## Related Source Files

- `src/main/java/org/hello/riscvdisassembler/core/discover/CodeDiscoveryEngine.java`
- `src/main/java/org/hello/riscvdisassembler/core/discover/DiscoveryMode.java`
- `src/main/java/org/hello/riscvdisassembler/core/discover/ControlFlowEdge.java`
- `src/main/java/org/hello/riscvdisassembler/core/discover/DiscoveredProgram.java`
- `src/main/java/org/hello/riscvdisassembler/core/discover/DiscoveredRegion.java`
- `src/main/java/org/hello/riscvdisassembler/core/discover/RegionClassifier.java`
- `src/main/java/org/hello/riscvdisassembler/core/discover/RegionKind.java`
- `src/main/java/org/hello/riscvdisassembler/core/decode/InstructionDecoder.java`
- `src/main/java/org/hello/riscvdisassembler/core/decode/Rv32iDecoder.java`
- `src/main/java/org/hello/riscvdisassembler/app/pipeline/DisassemblyPipeline.java`
- `src/test/java/org/hello/riscvdisassembler/core/discover/CodeDiscoveryEngineTest.java`

## Short Summary

If you need one sentence for the current project state, use this:

> Discovery is the control-flow-aware stage that decides what should be decoded, and `DiscoveredProgram` is the shared downstream contract that every output feature now consumes.
