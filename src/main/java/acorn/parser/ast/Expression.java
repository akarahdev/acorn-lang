package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import llvm4j.module.value.Constant;
import llvm4j.module.value.Identifier;
import llvm4j.module.value.Value;

import java.util.List;

public sealed interface Expression {
    default Value compileValue(CodeGenerator builder) {
        builder.codeBuilder().comment("ENTER " + this.toString().replaceAll("\n", "[nl]"));
        var h = this.compileInnerValue(builder);
        builder.codeBuilder().comment("EXIT " + this.toString().replaceAll("\n", "[nl]"));
        return h;
    }
    Value compileInnerValue(CodeGenerator builder);
    default Value compileInnerPath(CodeGenerator builder) {
        throw new RuntimeException(this + " can not be used as a pointer path");
    }
    AstType inferType(CodeGenerator builder);

    record Variable(String name) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            if(builder.context().functions().containsKey(name)) {
                return Identifier.global(builder.context().functions().get(name).mangling());
            }
            if(builder.stackMap().hasLocalVariable(name)) {
                return builder.codeBuilder().load(
                        builder.stackMap().getLocalVariable(name).type().toType(builder.context()),
                        this.compileInnerPath(builder)
                );
            }
            throw new RuntimeException("Unable to resolve variable " + name);
        }

        @Override
        public Value compileInnerPath(CodeGenerator builder) {
            if(builder.context().functions().containsKey(name)) {
                return Identifier.global(builder.context().functions().get(name).mangling());
            }
            if(builder.stackMap().hasLocalVariable(name)) {
                return builder.stackMap().getLocalVariable(name).stackSlot();
            }
            throw new RuntimeException("Unable to resolve path of variable " + name);
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            if(builder.context().functions().containsKey(name)) {
                return builder.context().functions().get(name).ptrType();
            }
            if(builder.stackMap().hasLocalVariable(name)) {
                return builder.stackMap().getLocalVariable(name).type();
            }
            throw new RuntimeException("Unable to infer type of variable " + name);
        }
    }

    record Invocation(Expression functionPointer, List<Expression> args) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            var ptrType = functionPointer.inferType(builder);
            var returnType = this.inferType(builder);
            if(returnType instanceof AstType.Void) {
                builder.codeBuilder().callVoid(
                        functionPointer.compileValue(builder).typed(ptrType.toType(builder.context())),
                        args.stream().map(x -> x.compileValue(builder).typed(x.inferType(builder).toType(builder.context()))).toList()
                );
                return null;
            } else {
                return builder.codeBuilder().callTyped(
                        functionPointer.compileValue(builder).typed(ptrType.toType(builder.context())),
                        args.stream().map(x -> x.compileValue(builder).typed(x.inferType(builder).toType(builder.context()))).toList()
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
        public Value compileInnerValue(CodeGenerator builder) {
            var exprType = this.inferType(builder).unbox().toType(builder.context());
            return builder.codeBuilder().add(exprType, left.compileValue(builder), right.compileValue(builder));
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            assert left.inferType(builder).equals(right.inferType(builder));
            return left.inferType(builder);
        }
    }

    record Integer(long value) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            return Constant.integer(value);
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.Integer(32);
        }
    }

    record CStringValue(String value) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            var g = Identifier.globalRandom();
            builder.module().withGlobalVariable(
                    g,
                    Constant.c_str(value + "\0")
            );
            return g;
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.LibCPointer();
        }
    }

    record StringValue(String value) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            throw new RuntimeException("Not yet implemented");
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.LibCPointer();
        }
    }

    record Box(Expression value) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            return builder.wrapValueInRefCount(
                    value.compileValue(builder).typed(value.inferType(builder).toType(builder.context())),
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
        public Value compileInnerValue(CodeGenerator builder) {
            return builder.loadValueFromRefCount(
                    value.inferType(builder).unbox().toType(builder.context()),
                    value.compileValue(builder)
            );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return value.inferType(builder).unbox();
        }
    }
}
