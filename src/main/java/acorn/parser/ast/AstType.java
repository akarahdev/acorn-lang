package acorn.parser.ast;

import acorn.parser.ctx.GlobalContext;
import llvm4j.module.type.Type;

import java.util.List;

public sealed interface AstType {
    default AstType unbox(GlobalContext context) {
        if(this instanceof Boxed(AstType type)) {
            return type.unbox(context);
        }
        if(this instanceof Unresolved(String name)) {
            if(context.typeAliases().containsKey(name)) {
                return context.typeAliases().get(name).unbox(context);
            } else {
                throw new RuntimeException(name + " is not a valid type");
            }
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

    record Any() implements AstType {
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
            return Type.struct(
                    parameters.stream().map(x -> x.type().toType(context)).toList()
            );
        }
    }

    record Array(AstType param) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.struct(List.of(
                    Type.integer(64),
                    Type.ptr()
            ));
        }
    }
}
