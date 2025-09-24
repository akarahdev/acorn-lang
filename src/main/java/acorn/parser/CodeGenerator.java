package acorn.parser;

import llvm4j.module.code.BasicBlock;

public record CodeGenerator(
        BasicBlock.Builder codeBuilder
) {
}
