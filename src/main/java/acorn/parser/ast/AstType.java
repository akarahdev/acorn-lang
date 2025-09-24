package acorn.parser.ast;

import llvm4j.module.type.Type;

import java.util.List;

public sealed interface AstType {
    Type toType();

    record Integer(int bits) implements AstType {
        @Override
        public Type toType() {
            return Type.integer(bits);
        }
    }

    record CString() implements AstType {
        @Override
        public Type toType() {
            return Type.ptr();
        }
    }

    record Function(AstType returned, List<AstType> parameters, boolean varargs) implements AstType {
        @Override
        public Type toType() {
            return Type.function(
                    returned.toType(),
                    parameters.stream().map(AstType::toType).toList(),
                    varargs
            );
        }
    }
}
