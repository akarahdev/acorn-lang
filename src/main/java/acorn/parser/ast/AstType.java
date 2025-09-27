package acorn.parser.ast;

import acorn.parser.ctx.GlobalContext;
import acorn.token.SpanData;
import acorn.token.SpannedException;
import java.util.List;
import java.util.stream.Collectors;
import llvm4j.module.type.Type;

public sealed interface AstType {
    default AstType unbox(GlobalContext context) {
        if (this instanceof Boxed(AstType type)) {
            return type.unbox(context);
        }
        if (this instanceof Unresolved(String name, SpanData span)) {
            if (context.typeAliases().containsKey(name)) {
                return context.typeAliases().get(name).unbox(context);
            } else {
                throw new SpannedException(
                    span,
                    new SpannedException.ErrorType.VariableDoesNotExist(name)
                );
            }
        }
        return this;
    }

    Type toType(GlobalContext context);

    default String typeName() {
        return this.toString();
    }

    SpanData span();

    record Unresolved(String name, SpanData span) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return context.typeAliases().get(this.name).toType(context);
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    record Boxed(AstType type) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.ptr();
        }

        @Override
        public String toString() {
            return this.type.typeName();
        }

        @Override
        public SpanData span() {
            return this.type.span();
        }
    }

    record Integer(int bits, SpanData span) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.integer(bits);
        }

        @Override
        public String toString() {
            return "i" + bits;
        }
    }

    record Void(SpanData span) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.voidType();
        }

        @Override
        public String toString() {
            return "void";
        }
    }

    record LibCPointer(SpanData span) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.ptr();
        }

        @Override
        public String toString() {
            return "libc::ptr";
        }
    }

    record Any(SpanData span) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.ptr();
        }

        @Override
        public String toString() {
            return "any";
        }
    }

    record Function(
        AstType returned,
        List<AstType> parameters,
        boolean varargs,
        SpanData span
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
        public String toString() {
            return (
                "fn(" +
                parameters
                    .stream()
                    .map(x -> x.toString())
                    .collect(Collectors.joining(", ")) +
                ") -> " +
                this.returned.toString()
            );
        }
    }

    record Struct(List<Header.Parameter> parameters, SpanData span) implements
        AstType {
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
        public String toString() {
            return (
                "{" +
                this.parameters.stream()
                    .map(x -> x.name())
                    .collect(Collectors.joining(",")) +
                "}"
            );
        }
    }

    record Array(AstType param, SpanData span) implements AstType {
        @Override
        public Type toType(GlobalContext context) {
            return Type.struct(List.of(Type.integer(64), Type.ptr()));
        }

        @Override
        public String toString() {
            return ("[" + this.param.typeName() + "]");
        }
    }
}
