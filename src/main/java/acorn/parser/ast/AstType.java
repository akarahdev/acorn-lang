package acorn.parser.ast;

import acorn.parser.ctx.GlobalContext;
import java.util.List;
import java.util.stream.Collectors;
import llvm4j.module.type.Type;

public sealed interface AstType {
    default AstType unbox(GlobalContext context) {
        if (this instanceof Boxed(AstType type)) {
            return type.unbox(context);
        }
        if (this instanceof Unresolved(String name)) {
            if (context.typeAliases().containsKey(name)) {
                return context.typeAliases().get(name).unbox(context);
            } else {
                throw new RuntimeException(name + " is not a valid type");
            }
        }
        return this;
    }

    Type toType(GlobalContext context);
    String typeName();

    record Unresolved(String name) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return context.typeAliases().get(this.name).toType(context);
        }

        @Override
        public String typeName() {
            return this.name;
        }
    }

    record Boxed(AstType type) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.ptr();
        }

        @Override
        public String typeName() {
            return this.type.typeName().replace("raw::", "");
        }
    }

    record Integer(int bits) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.integer(bits);
        }

        @Override
        public String typeName() {
            return "raw_i" + bits;
        }
    }

    record Void() implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.voidType();
        }

        @Override
        public String typeName() {
            return "void";
        }
    }

    record LibCPointer() implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.ptr();
        }

        @Override
        public String typeName() {
            return "raw::libc::ptr";
        }
    }

    record Any() implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.ptr();
        }

        @Override
        public String typeName() {
            return "raw::any";
        }
    }

    record Function(
        AstType returned,
        List<AstType> parameters,
        boolean varargs
    ) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.function(
                returned.toType(context),
                parameters
                    .stream()
                    .map(x -> x.toType(context))
                    .toList(),
                varargs
            );
        }

        @Override
        public String typeName() {
            return "raw::fn";
        }
    }

    record Struct(List<Header.Parameter> parameters) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.struct(
                parameters
                    .stream()
                    .map(x -> x.type().toType(context))
                    .toList()
            );
        }

        @Override
        public String typeName() {
            return (
                "raw::struct(" +
                this.parameters.stream()
                    .map(x -> x.name())
                    .collect(Collectors.joining(",")) +
                ")"
            );
        }
    }

    record Array(AstType param) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.struct(List.of(Type.integer(64), Type.ptr()));
        }

        @Override
        public String typeName() {
            return ("raw::array::" + this.param.typeName());
        }
    }
}
