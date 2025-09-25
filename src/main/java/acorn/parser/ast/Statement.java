package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import llvm4j.module.type.Type;

public sealed interface Statement {
    void compile(CodeGenerator gen);

    record Ret(Expression expr) implements Statement {
        @Override
        public void compile(CodeGenerator gen) {
            if(expr == null) {
                gen.codeBuilder().ret();
                return;
            }
            gen.codeBuilder().ret(expr.compile(gen).typed(expr.inferType(gen).toType(gen.context())));
        }
    }

    record Dropping(Expression expr) implements Statement {
        @Override
        public void compile(CodeGenerator gen) {
            expr.compile(gen);
        }
    }
}
