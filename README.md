# RISC-V Disassembler

Project nay la mot RV32I ELF disassembler viet bang Java 21, to chuc theo pipeline ro rang de vua phu hop do an hoc thuat, vua de mo rong thanh mot cong cu phan tich nhi phan nho gon.

## Muc tieu

Ung dung tap trung vao:

- doc va phan tich ELF32 little-endian
- chuan hoa input thanh `BinaryImage`
- resolve section va symbol thanh `ResolvedProgram`
- discovery dia chi code theo `LINEAR` hoac `RECURSIVE`
- decode instruction RV32I thanh `InstructionIr`
- xuat ket qua duoi dang `asm`, `json`, hoac `cfg`

No khong phai emulator, khong phai linker/loader day du, va hien tai cung chua phai decompiler.

## Kien truc hien tai

Project da duoc to chuc lai theo cac lop sau:

1. `entry`
   - adapter dau vao cho nguoi dung
   - gom CLI va JavaFX UI
2. `app`
   - orchestration layer
   - dieu phoi pipeline dung chung cho CLI va UI
3. `adapters`
   - adapter theo dinh dang input
   - hien tai co `adapters.input.elf`
4. `core`
   - phan xu ly chinh cua he thong
   - gom `binary`, `resolve`, `discover`, `decode`
5. `features`
   - cac downstream output modules
   - gom `text`, `json`, `cfg`, `header`

## Cau truc thu muc

```text
src/main/java/org/hello/riscvdisassembler
|-- Main.java
|-- entry
|   |-- cli
|   `-- ui
|-- app
|   `-- pipeline
|-- adapters
|   `-- input
|       `-- elf
|           `-- model
|-- core
|   |-- binary
|   |   `-- model
|   |-- resolve
|   |-- discover
|   `-- decode
|       `-- model
`-- features
    |-- text
    |-- json
    |-- cfg
    `-- header
```

## Luong xu ly

```text
CLI/UI
  -> DisassemblyRequest
  -> ElfLoader
  -> ElfBinaryImageAdapter
  -> BinaryImage
  -> SectionSymbolResolver
  -> ResolvedProgram
  -> CodeDiscoveryEngine
  -> DiscoveredProgram
  -> TextEmitter / JsonEmitter / CfgBuilder
```

Nhanh `header-only` se bo qua resolve/discovery/decode va di truc tiep:

```text
CLI/UI -> DisassemblyRequest -> ElfLoader.loadHeader() -> HeaderEmitter
```

## Y nghia cua cac model chinh

- `ElfFile`, `ElfHeader`, `SectionHeader`, `SymbolEntry`
  - DTO cua adapter ELF
  - chi dung cho input format ELF
- `BinaryImage`, `BinarySection`, `BinarySymbol`
  - canonical input model cho core pipeline
- `ResolvedProgram`
  - ket qua resolve section executable va symbol lookup
- `InstructionIr`
  - ket qua decode instruction
  - DTO cua decode stage
- `DiscoveredProgram`
  - contract downstream chung cho text/json/cfg
  - chua `instructions`, `edges`, `regions`, `mode`

## Discovery model
Thay vi chi linear sweep toan bo section, pipeline hien tai co `CodeDiscoveryEngine` nam giua resolve va decode:

- `RECURSIVE` la mode mac dinh
- `LINEAR` van duoc giu lai cho inspection mode
- `--disassemble-all` hien tai se dung `LINEAR`

Recursive discovery:

- seed tu entry point va executable symbols dang tin
- theo direct branch/jump/call targets
- bo qua cac data label yeu nhu `$d`, `.` labels
- classify cac khoang unreachable thanh `regions`

Hien tai system da co:

- `ControlFlowEdge`
- `DiscoveredRegion`
- `RegionClassifier`
- `DiscoveryMode`

## Output modules

Tat ca output modules hien tai deu nhan cung mot contract la `DiscoveredProgram`.

- `features.text.TextEmitter`
  - xuat asm text
  - co the hien code region va data region
- `features.json.JsonEmitter`
  - xuat JSON co `instructions`, `edges`, `regions`, `discoveryMode`
- `features.cfg.CfgBuilder`
  - dung `edges` da duoc discovery sinh ra
  - gom instruction thanh basic block va xuat CFG summary
- `features.header.HeaderEmitter`
  - xuat ELF header summary

## CLI

CLI ho tro:

- `--input`, `-i`
- `--format`, `-f`
- `--output`, `-o`
- `--header-only`, `-H`
- `--disassemble-all`, `-a`
- `--ui`, `-u`
- `--debug`, `-d`
- `--help`, `-h`

Format hop le:

- `asm`
- `json`
- `cfg`

## UI

JavaFX UI dung chung `DisassemblyPipeline` voi CLI.

UI ho tro:

- chon input file
- chon output format
- output file tuy chon
- header-only
- disassemble-all
- debug stack trace

## Build va chay

Build:

```bash
mvn package
```

Test:

```bash
mvn test
```

Jar sau khi package:

```bash
target/riscv-disassembler.jar
```

Chay CLI:

```bash
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format asm
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format json
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format cfg
java -jar target/riscv-disassembler.jar --input samples/sample.elf --header-only
java -jar target/riscv-disassembler.jar -i samples/sample.elf -f asm
java -jar target/riscv-disassembler.jar -i samples/sample.elf -H
```

Chay UI:

```bash
java -jar target/riscv-disassembler.jar --ui
```

## Test coverage hien tai

Repo hien co regression tests cho:

- ELF loading
- resolver
- decoder
- discovery
- emitters
- CFG builder
- CLI
- pipeline

So luong test pass hien tai: `27`.

## Trang thai ky thuat hien tai

Da hoan thanh:

- refactor package layout sang `entry/app/adapters/core/features`
- them canonical input model `BinaryImage`
- tach core ra khoi ELF-specific DTO
- them discovery layer voi recursive traversal
- giu linear mode nhu mot inspection mode
- chuan hoa downstream contract thanh `DiscoveredProgram`
- bo logic rediscover control flow khoi `CfgBuilder`
- them region classification co ban cho unreachable gaps

Chua hoan thanh:

- CLI option chon discovery mode mot cach explicit
- heuristic `ALIGNMENT` / `UNKNOWN`
- jump table recognition
- indirect branch target recovery
- pseudo-instruction handling
- relocation-aware analysis
- DOT/Graphviz export
- support RV32M / RV32C

## Tai lieu lien quan

- `docs/project-context-summary.md`
- `docs/discovery-module.md`
- `docs/wsl-general-cheatsheet.md`

## Ghi chu

Neu ban xem repo nhu mot kien truc, thi nen hieu:

- `adapters.input.elf` la adapter layer
- `core.binary.model` la input contract cua core
- `core.discover.DiscoveredProgram` la output contract cua phan disassembly
- `features.*` la cac module tieu thu ket qua cuoi

Day la cach doc dung nhat voi codebase hien tai.
