package acorn.parser.ast;

import llvm4j.module.type.Type;

public sealed interface AstType {
    Type toType();

    record Integer(int bits) implements AstType {
        @Override
        public Type toType() {
            return Type.integer(bits);
        }
    }
}
