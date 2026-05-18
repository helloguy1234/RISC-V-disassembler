package org.hello.riscvdisassembler.core.discover.indirect;

import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.discover.CodeDiscoveryEngine;
import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.hello.riscvdisassembler.adapters.input.elf.ElfLoader;
import org.hello.riscvdisassembler.adapters.input.elf.ElfBinaryImageAdapter;
import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.hello.riscvdisassembler.core.resolve.SectionSymbolResolver;
import org.hello.riscvdisassembler.core.decode.Rv32iDecoder;
import org.hello.riscvdisassembler.core.discover.DiscoveryMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndirectBranchIntegrationTest {

    private DiscoveredProgram runDiscovery(String resourcePath) throws Exception {
        URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
        assertNotNull(resourceUrl, "Không tìm thấy file test trong resources: " + resourcePath);
        Path elfPath = Paths.get(resourceUrl.toURI());

        ElfLoader loader = new ElfLoader();
        var elfFile = loader.load(elfPath);
        BinaryImage image = new ElfBinaryImageAdapter().adapt(elfFile);
        ResolvedProgram resolved = new SectionSymbolResolver().resolve(image);
        CodeDiscoveryEngine engine = new CodeDiscoveryEngine(new Rv32iDecoder());
        return engine.discover(resolved, DiscoveryMode.RECURSIVE);
    }

    @Test
    @DisplayName("Tình huống 1: Bảng nhảy chuẩn (Standard Jump Table)")
    void testStandardJumpTable() throws Exception {
        DiscoveredProgram program = runDiscovery("samples/jump_table_simple/jump_table_simple.elf");
        List<InstructionIr> instructions = program.instructions();

        long jalrCount = instructions.stream().filter(i -> "jalr".equals(i.mnemonic())).count();
        assertTrue(jalrCount > 0, "Phải tìm thấy lệnh jalr");

        // Kiểm tra xem engine có phân giải thành công tất cả 4 case (10, 20, 30, 40)
        // không
        long addiCount = instructions.stream()
                .filter(i -> "addi".equals(i.mnemonic()) && i.operands().toString().matches(".*(10|20|30|40).*"))
                .count();
        assertEquals(4, addiCount, "Engine phải nội suy và decode thành công 4 nhánh, không được phép coi là data rác");
    }

    @Test
    @DisplayName("Tình huống 2: Phân tích qua Lớp tương đương (Equivalence Class)")
    void testEquivalenceClass() throws Exception {
        DiscoveredProgram program = runDiscovery("samples/jump_table_equiv_class/jump_table_equiv_class.elf");
        List<InstructionIr> instructions = program.instructions();

        long jalrCount = instructions.stream().filter(i -> "jalr".equals(i.mnemonic())).count();
        assertTrue(jalrCount > 0, "Phải tìm thấy lệnh jalr");

        long addiCount = instructions.stream()
                .filter(i -> "addi".equals(i.mnemonic()) && i.operands().toString().matches(".*(10|20|30|40).*"))
                .count();
        assertEquals(4, addiCount, "Phải giải mã đủ 4 case dù Index bị sao chép sang thanh ghi khác (Lớp tương đương)");
    }

    @Test
    @DisplayName("Tình huống 3: Dừng an toàn do vượt ngưỡng (Exceed Threshold)")
    void testExceedThresholdSafeFallback() throws Exception {
        DiscoveredProgram program = runDiscovery("samples/jump_table_exceed_threshold/jump_table_exceed_threshold.elf");
        List<InstructionIr> instructions = program.instructions();

        // Đảm bảo engine chạy xong mà không bị lặp vô hạn/crash
        assertNotNull(program, "Engine phải kết thúc trơn tru và trả về kết quả");
        long addiCount = instructions.stream()
                .filter(i -> "addi".equals(i.mnemonic()) && i.operands().toString().matches(".*(10|20|30|40).*"))
                .count();
        assertTrue(addiCount < 4, "Thuật toán phải dừng an toàn, từ chối phân giải và không tạo ra các target sai");
    }
}