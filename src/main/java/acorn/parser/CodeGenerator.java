package acorn.parser;

import acorn.parser.ctx.GlobalContext;
import llvm4j.module.Function;
import llvm4j.module.Module;
import llvm4j.module.code.BasicBlock;

public record CodeGenerator(
        GlobalContext context,
        Module.Builder module,
        Function.Builder function,
        BasicBlock.Builder codeBuilder
) {
}
