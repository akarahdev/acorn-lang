package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import llvm4j.module.value.Constant;
import llvm4j.module.value.Identifier;
import llvm4j.module.value.Value;

import java.util.List;

public sealed interface Expression {
    default Value compile(CodeGenerator builder) {
        builder.codeBuilder().comment("ENTER " + this.toString().replaceAll("\n", "[nl]"));
        var h = this.compileInner(builder);
        builder.codeBuilder().comment("EXIT " + this.toString().replaceAll("\n", "[nl]"));
        return h;
    }
    Value compileInner(CodeGenerator builder);
    AstType inferType(CodeGenerator builder);

    record Variable(String name) implements Expression {
        @Override
        public Value compileInner(CodeGenerator builder) {
            if(builder.context().functions().containsKey(name)) {
                return Identifier.global(builder.context().functions().get(name).mangling());
            }
            throw new RuntimeException("Unable to resolve variable " + name);
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            if(builder.context().functions().containsKey(name)) {
                return builder.context().functions().get(name).ptrType();
            }
            throw new RuntimeException("Unable to infer type of variable " + name);
        }
    }

    record Invocation(Expression functionPointer, List<Expression> args) implements Expression {
        @Override
        public Value compileInner(CodeGenerator builder) {
            var ptrType = functionPointer.inferType(builder);
            var returnType = this.inferType(builder);
            if(returnType instanceof AstType.Void) {
                builder.codeBuilder().callVoid(
                        functionPointer.compile(builder).typed(ptrType.toType(builder.context())),
                        args.stream().map(x -> x.compile(builder).typed(x.inferType(builder).toType(builder.context()))).toList()
                );
                return null;
            } else {
                return builder.codeBuilder().callTyped(
                        functionPointer.compile(builder).typed(ptrType.toType(builder.context())),
                        args.stream().map(x -> x.compile(builder).typed(x.inferType(builder).toType(builder.context()))).toList()
                );
            }
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return ((AstType.Function) functionPointer.inferType(builder)).returned();
        }
    }

    record Addition(Expression left, Expression right) implements Expression {
        @Override
        public Value compileInner(CodeGenerator builder) {
            return builder.codeBuilder().add(this.inferType(builder).unbox().toType(builder.context()), left.compile(builder), right.compile(builder));
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            assert left.inferType(builder).equals(right.inferType(builder));
            return left.inferType(builder);
        }
    }

    record Integer(long value) implements Expression {
        @Override
        public Value compileInner(CodeGenerator builder) {
            return Constant.integer(value);
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.Integer(32);
        }
    }

    record CStringValue(String value) implements Expression {
        @Override
        public Value compileInner(CodeGenerator builder) {
            var g = Identifier.globalRandom();
            builder.module().withGlobalVariable(
                    g,
                    Constant.c_str(value + "\0")
            );

            return g;
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.CString();
        }
    }

    record StringValue(String value) implements Expression {
        @Override
        public Value compileInner(CodeGenerator builder) {
            throw new RuntimeException("Not yet implemented");
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.CString();
        }
    }

    record Box(Expression value) implements Expression {
        @Override
        public Value compileInner(CodeGenerator builder) {
            return builder.wrapValueInRefCount(
                    value.compile(builder).typed(value.inferType(builder).toType(builder.context())),
                    128
            );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.Boxed(value.inferType(builder));
        }
    }

    record Unbox(Expression value) implements Expression {
        @Override
        public Value compileInner(CodeGenerator builder) {
            return builder.loadValueFromRefCount(
                    value.inferType(builder).unbox().toType(builder.context()),
                    value.compile(builder)
            );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return value.inferType(builder).unbox();
        }
    }
}
