package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import llvm4j.module.value.Constant;
import llvm4j.module.value.Identifier;
import llvm4j.module.value.Value;

import java.util.List;

public sealed interface Expression {
    Value compile(CodeGenerator builder);
    AstType inferType(CodeGenerator builder);

    record Variable(String name) implements Expression {
        @Override
        public Value compile(CodeGenerator builder) {
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
        public Value compile(CodeGenerator builder) {
            return builder.codeBuilder().call(
                    functionPointer.compile(builder).typed(functionPointer.inferType(builder).toType()),
                    args.stream().map(x -> x.compile(builder).typed(x.inferType(builder).toType())).toList()
            );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return ((AstType.Function) functionPointer.inferType(builder)).returned();
        }
    }

    record Addition(Expression left, Expression right) implements Expression {
        @Override
        public Value compile(CodeGenerator builder) {
            return builder.codeBuilder().add(this.inferType(builder).toType(), left.compile(builder), right.compile(builder));
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            assert left.inferType(builder).equals(right.inferType(builder));
            return left.inferType(builder);
        }
    }

    record Integer(long value) implements Expression {
        @Override
        public Value compile(CodeGenerator builder) {
            return Constant.integer(value);
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.Integer(32);
        }
    }

    record StringValue(String value) implements Expression {
        @Override
        public Value compile(CodeGenerator builder) {
            var g = Identifier.globalRandom();
            builder.module().withGlobalVariable(
                    g,
                    Constant.c_str(value)
            );
            return g;
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.CString();
        }
    }
}
