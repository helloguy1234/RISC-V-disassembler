# RISC-V Disassembler

Du an nay duoc to chuc lai theo pipeline hoc thuat nhung van thuc te de trien khai thanh do an:

1. Input / CLI
2. ELF Loader / File Parser
3. Section + Symbol Resolver
4. RV32I Decoder
5. Instruction IR
6. Text Emitter / JSON Output / CFG Builder

## Cau truc thu muc

```text
src/main/java/org/hello/riscvdisassembler
|-- Main.java
|-- cli
|   `-- DisassemblerCli.java
|-- pipeline
|   `-- DisassemblyPipeline.java
|-- elf
|   |-- ElfLoader.java
|   |-- ElfParsingException.java
|   `-- model
|       |-- ElfFile.java
|       |-- ElfHeader.java
|       |-- SectionHeader.java
|       `-- SymbolEntry.java
|-- resolver
|   |-- ResolvedProgram.java
|   `-- SectionSymbolResolver.java
|-- decoder
|   `-- Rv32iDecoder.java
|-- ir
|   `-- InstructionIr.java
|-- emit
|   |-- JsonEmitter.java
|   `-- TextEmitter.java
`-- analysis
    |-- BasicBlock.java
    `-- CfgBuilder.java
```

## Luong xu ly

```text
Input/CLI -> ELF Loader -> Resolver -> RV32I Decoder -> Instruction IR
                                                     |-> Text Emitter
                                                     |-> JSON Output
                                                     `-> CFG Builder
```

## Cach build

```bash
mvn clean package
```

Sau khi build, file chay truc tiep se nam tai:

```bash
target/riscv-disassembler.jar
```

## Cach chay

```bash
java -jar target/riscv-disassembler.jar --input path/to/file.elf --format asm
java -jar target/riscv-disassembler.jar --input path/to/file.elf --format json
java -jar target/riscv-disassembler.jar --input path/to/file.elf --format cfg
java -jar target/riscv-disassembler.jar --input path/to/file.elf --header-only
```

Vi du voi file mau trong repo:

```bash
java -jar target/riscv-disassembler.jar --input samples/sample.elf --format asm
```

Tuy chon ho tro:

- `--input <file>`: tep ELF can disassemble
- `--format <asm|json|cfg>`: dinh dang dau ra
- `--header-only`: chi parse ELF header de debug file loi / file chua duoc ho tro
- `--output <file>`: ghi ket qua ra tep
- `--debug`: in full stack trace khi co loi

## Ghi chu hoc thuat

- `ElfLoader` tap trung vao ELF32 little-endian, phu hop voi RV32I.
- `InstructionIr` la tang trung gian de tach biet decoder voi cac emitter va phan tich.
- `CfgBuilder` xay CFG co ban dua tren branch/jump/return, du de trinh bay trong bao cao mon hoc va mo rong ve sau.

## Tai lieu va du lieu mau

- Thu muc `samples/` chua file ELF mau va output disassembly mau.
- Thu muc `docs/` chua bao cao va hinh anh minh hoa.

## Huong mo rong

- Ho tro relocation va pseudo-instruction
- Ho tro RV32M/RV32C
- Phan tich du lieu va dead code
- Xuat DOT/Graphviz cho CFG
