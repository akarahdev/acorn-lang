package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import llvm4j.module.type.Type;
import llvm4j.module.value.Constant;
import llvm4j.module.value.Value;

public sealed interface Expression {
    Value compile(CodeGenerator builder);

    record Addition(Expression left, Expression right) implements Expression {
        @Override
        public Value compile(CodeGenerator gen) {
            return gen.codeBuilder().add(Type.integer(32), left.compile(gen), right.compile(gen));
        }
    }

    record Integer(long value) implements Expression {
        @Override
        public Value compile(CodeGenerator gen) {
            return Constant.integer(value);
        }
    }
}
