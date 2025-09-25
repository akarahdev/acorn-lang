package acorn.parser.ast;

import acorn.parser.ctx.GlobalContext;
import llvm4j.module.type.Type;

import java.util.List;

public sealed interface AstType {
    default AstType unbox() {
        if(this instanceof Boxed(AstType type)) {
            return type;
        }
        return this;
    }

    Type toType(GlobalContext context);

    record Unresolved(String name) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return context.typeAliases().get(this.name).toType(context);
        }
    }

    record Boxed(AstType type) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.ptr();
        }
    }

    record Integer(int bits) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.integer(bits);
        }
    }

    record Void() implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.voidType();
        }
    }

    record LibCPointer() implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.ptr();
        }
    }

    record Function(AstType returned, List<AstType> parameters, boolean varargs) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.function(
                    returned.toType(context),
                    parameters.stream().map(x -> x.toType(context)).toList(),
                    varargs
            );
        }
    }

    record Struct(List<Header.Parameter> parameters) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.struct(List.of(
                    Type.integer(32),
                    Type.integer(32),
                    Type.ptr()
            ));
        }
    }
}
