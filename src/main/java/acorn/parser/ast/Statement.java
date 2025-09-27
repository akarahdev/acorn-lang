package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import acorn.parser.ctx.FunctionRecord;
import acorn.token.SpanData;
import acorn.token.SpannedException;
import java.util.List;

public sealed interface Statement {
    default void compile(CodeGenerator gen, FunctionRecord function) {
        gen
            .codeBuilder()
            .comment("ENTER STATEMENT " + this.toString().replace("\n", "[n]"));
        this.typeCheck(gen, function);
        this.compileInner(gen, function);
        gen
            .codeBuilder()
            .comment("EXIT STATEMENT " + this.toString().replace("\n", "[n]"));
    }

    void compileInner(CodeGenerator gen, FunctionRecord function);
    void typeCheck(CodeGenerator gen, FunctionRecord functionRecord);

    record Ret(Expression expr) implements Statement {
        @Override
        public void compileInner(CodeGenerator gen, FunctionRecord function) {
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

        @Override
        public void typeCheck(CodeGenerator gen, FunctionRecord function) {
            if (expr == null) {
                if (
                    !(function
                            .function()
                            .returnType()
                            .typeEquals(
                                new AstType.Void(function.function().span())
                            ))
                ) {
                    throw new SpannedException(
                        this.expr.span(),
                        new SpannedException.ErrorType.WrongType(
                            List.of(function.function().returnType()),
                            new AstType.Void(function.function().span())
                        )
                    );
                }
            } else {
                if (
                    !(function
                            .function()
                            .returnType()
                            .typeEquals(expr.inferType(gen)))
                ) {
                    throw new SpannedException(
                        this.expr.span(),
                        new SpannedException.ErrorType.WrongType(
                            List.of(function.function().returnType()),
                            expr.inferType(gen)
                        )
                    );
                }
            }
        }
    }

    record StoreValue(Expression path, Expression expr) implements Statement {
        @Override
        public void compileInner(CodeGenerator gen, FunctionRecord function) {
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

        @Override
        public void typeCheck(CodeGenerator gen, FunctionRecord function) {}
    }

    record Dropping(Expression expr) implements Statement {
        @Override
        public void compileInner(CodeGenerator gen, FunctionRecord function) {
            expr.compileValue(gen);
        }

        @Override
        public void typeCheck(
            CodeGenerator gen,
            FunctionRecord functionRecord
        ) {
            this.expr.typecheck(gen);
        }
    }
}
