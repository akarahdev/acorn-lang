package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import acorn.token.SpanData;

public sealed interface Statement {
    default void compile(CodeGenerator gen) {
        gen
            .codeBuilder()
            .comment("ENTER STATEMENT " + this.toString().replace("\n", "[n]"));
        this.compileInner(gen);
        gen
            .codeBuilder()
            .comment("EXIT STATEMENT " + this.toString().replace("\n", "[n]"));
    }

    void compileInner(CodeGenerator gen);

    record Ret(Expression expr) implements Statement {
        @Override
        public void compileInner(CodeGenerator gen) {
            if (expr == null) {
                gen.codeBuilder().ret();
                return;
            }
            gen
                .codeBuilder()
                .ret(
                    expr
                        .compileValue(gen)
                        .typed(expr.inferType(gen).toType(gen.context()))
                );
        }
    }

    record StoreValue(Expression path, Expression expr) implements Statement {
        @Override
        public void compileInner(CodeGenerator gen) {
            if (
                path instanceof
                    Expression.Variable(String variableName, SpanData spanData)
            ) {
                if (!gen.stackMap().hasLocalVariable(variableName)) {
                    gen
                        .stackMap()
                        .storeVariable(
                            variableName,
                            gen
                                .codeBuilder()
                                .alloca(
                                    expr.inferType(gen).toType(gen.context())
                                ),
                            expr.inferType(gen),
                            spanData
                        );
                }
            }
            gen
                .codeBuilder()
                .store(
                    expr
                        .compileValue(gen)
                        .typed(expr.inferType(gen).toType(gen.context())),
                    path.compilePath(gen)
                );
        }
    }

    record Dropping(Expression expr) implements Statement {
        @Override
        public void compileInner(CodeGenerator gen) {
            expr.compileValue(gen);
        }
    }
}
