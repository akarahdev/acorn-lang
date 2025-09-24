package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import llvm4j.module.type.Type;

public sealed interface Statement {
    void compile(CodeGenerator gen);

    record Ret(Expression expr) implements Statement {
        @Override
        public void compile(CodeGenerator gen) {
            gen.codeBuilder().ret(expr.compile(gen).typed(Type.integer(32)));
        }
    }

    record Dropping(Expression expr) implements Statement {
        @Override
        public void compile(CodeGenerator gen) {
            expr.compile(gen);
        }
    }
}
