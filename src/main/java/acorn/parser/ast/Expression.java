package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import acorn.token.SpanData;
import acorn.token.SpannedException;
import java.util.List;
import llvm4j.module.type.Type;
import llvm4j.module.value.Constant;
import llvm4j.module.value.Identifier;
import llvm4j.module.value.Value;

public sealed interface Expression {
    SpanData span();

    default Value compileValue(CodeGenerator builder) {
        builder
            .codeBuilder()
            .comment("ENTER " + this.toString().replaceAll("\n", "[nl]"));
        var h = this.compileInnerValue(builder);
        builder
            .codeBuilder()
            .comment("EXIT " + this.toString().replaceAll("\n", "[nl]"));
        builder.codeBuilder().comment(" -- compiled into " + h);
        return h;
    }

    Value compileInnerValue(CodeGenerator builder);

    default Value compilePath(CodeGenerator builder) {
        builder
            .codeBuilder()
            .comment("ENTER PATH " + this.toString().replaceAll("\n", "[nl]"));
        var ip = compileInnerPath(builder);
        builder
            .codeBuilder()
            .comment("EXIT PATH " + this.toString().replaceAll("\n", "[nl]"));
        builder.codeBuilder().comment(" -- compiled into " + ip);
        return ip;
    }

    default Value compileInnerPath(CodeGenerator builder) {
        if (this instanceof Box(Expression expr)) {
            throw new SpannedException(
                expr.span(),
                new SpannedException.ErrorType.DoesNotSupportPathing(
                    this.inferType(builder)
                )
            );
        }
        if (this instanceof Unbox(Expression value)) {
            return builder.loadObjPtrFromWrapper(value.compilePath(builder));
        }
        throw new SpannedException(
            this.span(),
            new SpannedException.ErrorType.DoesNotSupportPathing(
                this.inferType(builder)
            )
        );
    }

    AstType inferType(CodeGenerator builder);

    default Expression debox() {
        if (this instanceof Box(Expression value)) {
            return value.debox();
        }
        if (this instanceof Unbox(Expression value)) {
            return value.debox();
        }
        return this;
    }

    record Variable(String name, SpanData span) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            if (builder.context().functions().containsKey(name)) {
                return Identifier.global(
                    builder.context().functions().get(name).mangling()
                );
            }
            if (builder.stackMap().hasLocalVariable(name)) {
                return builder
                    .codeBuilder()
                    .load(
                        builder
                            .stackMap()
                            .getLocalVariable(name, this.span)
                            .type()
                            .toType(builder.context()),
                        this.compilePath(builder)
                    );
            }
            throw new SpannedException(
                this.span,
                new SpannedException.ErrorType.VariableDoesNotExist(name)
            );
        }

        @Override
        public Value compileInnerPath(CodeGenerator builder) {
            if (builder.context().functions().containsKey(name)) {
                return Identifier.global(
                    builder.context().functions().get(name).mangling()
                );
            }
            if (builder.stackMap().hasLocalVariable(name)) {
                return builder
                    .stackMap()
                    .getLocalVariable(name, this.span)
                    .stackSlot();
            }
            throw new SpannedException(
                this.span(),
                new SpannedException.ErrorType.VariableDoesNotExist(this.name)
            );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            if (builder.context().functions().containsKey(name)) {
                return builder.context().functions().get(name).ptrType();
            }
            if (builder.stackMap().hasLocalVariable(name)) {
                return builder
                    .stackMap()
                    .getLocalVariable(name, this.span)
                    .type();
            }
            throw new SpannedException(
                this.span(),
                new SpannedException.ErrorType.VariableDoesNotExist(this.name)
            );
        }
    }

    record Invocation(
        Expression functionPointer,
        List<Expression> args,
        SpanData span
    ) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            var ptrType = functionPointer.inferType(builder);
            var returnType = this.inferType(builder);
            if (returnType instanceof AstType.Void) {
                builder
                    .codeBuilder()
                    .callVoid(
                        functionPointer
                            .compileValue(builder)
                            .typed(ptrType.toType(builder.context())),
                        args
                            .stream()
                            .map(x ->
                                x
                                    .compileValue(builder)
                                    .typed(
                                        x
                                            .inferType(builder)
                                            .toType(builder.context())
                                    )
                            )
                            .toList()
                    );
                return null;
            } else {
                return builder
                    .codeBuilder()
                    .callTyped(
                        functionPointer
                            .compileValue(builder)
                            .typed(ptrType.toType(builder.context())),
                        args
                            .stream()
                            .map(x ->
                                x
                                    .compileValue(builder)
                                    .typed(
                                        x
                                            .inferType(builder)
                                            .toType(builder.context())
                                    )
                            )
                            .toList()
                    );
            }
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return (
                (AstType.Function) functionPointer.inferType(builder)
            ).returned();
        }
    }

    record Addition(Expression left, Expression right, SpanData span) implements
        Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            var exprType = this.inferType(builder)
                .unbox(builder.context())
                .toType(builder.context());
            return builder
                .codeBuilder()
                .add(
                    exprType,
                    left.compileValue(builder),
                    right.compileValue(builder)
                );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            assert left.inferType(builder).equals(right.inferType(builder));
            return left.inferType(builder);
        }
    }

    record Integer(long value, SpanData span) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            return Constant.integer(value);
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.Integer(32, span);
        }
    }

    record CStringValue(String value, SpanData span) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            var g = Identifier.globalRandom();
            builder
                .module()
                .withGlobalVariable(g, Constant.c_str(value + "\0"));
            return g;
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.LibCPointer(span);
        }
    }

    record StringValue(String value, SpanData span) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            throw new RuntimeException("Not yet implemented");
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.LibCPointer(span);
        }
    }

    record Box(Expression value) implements Expression {
        @Override
        public SpanData span() {
            return this.value.span();
        }

        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            return builder.wrapValueInRefCount(
                value
                    .compileValue(builder)
                    .typed(value.inferType(builder).toType(builder.context())),
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
        public SpanData span() {
            return this.value.span();
        }

        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            return builder.loadValueFromRefCount(
                value
                    .inferType(builder)
                    .unbox(builder.context())
                    .toType(builder.context()),
                value.compileValue(builder)
            );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return value.inferType(builder).unbox(builder.context());
        }
    }

    record FieldAccess(
        Expression baseValuePtr,
        String identifier,
        SpanData span
    ) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            var baseType = baseValuePtr.inferType(builder);
            if (
                baseType.unbox(builder.context()) instanceof AstType.Array _ &&
                identifier.equals("length")
            ) {
                var loadedFromOriginal = builder
                    .codeBuilder()
                    .load(Type.ptr(), baseValuePtr.compilePath(builder));
                var objPtrFromWrapper = builder.loadObjPtrFromWrapper(
                    loadedFromOriginal
                );
                var objectAsValue = builder
                    .codeBuilder()
                    .load(
                        baseValuePtr
                            .inferType(builder)
                            .unbox(builder.context())
                            .toType(builder.context()),
                        objPtrFromWrapper
                    );
                return builder.wrapValueInRefCount(
                    builder
                        .codeBuilder()
                        .extractValue(
                            objectAsValue.typed(
                                baseValuePtr
                                    .inferType(builder)
                                    .unbox(builder.context())
                                    .toType(builder.context())
                            ),
                            0
                        )
                        .typed(Type.integer(64)),
                    8
                );
            }
            if (baseType.unbox(builder.context()) instanceof AstType.Struct _) {
                return builder
                    .codeBuilder()
                    .load(
                        this.inferType(builder).toType(builder.context()),
                        this.compilePath(builder)
                    );
            }
            throw new SpannedException(
                this.span(),
                new SpannedException.ErrorType.DoesNotSupportField(
                    baseType,
                    this.identifier
                )
            );
        }

        @Override
        public Value compileInnerPath(CodeGenerator builder) {
            var baseType = baseValuePtr.inferType(builder);
            if (baseType.unbox(builder.context()) instanceof AstType.Array) {
                var loadedFromOriginal = builder
                    .codeBuilder()
                    .load(Type.ptr(), baseValuePtr.compilePath(builder));
                var objPtrFromWrapper = builder.loadObjPtrFromWrapper(
                    loadedFromOriginal
                );
                var objectAsValue = builder
                    .codeBuilder()
                    .load(
                        baseValuePtr
                            .inferType(builder)
                            .unbox(builder.context())
                            .toType(builder.context()),
                        objPtrFromWrapper
                    );
                return builder
                    .codeBuilder()
                    .extractValue(
                        objectAsValue.typed(
                            baseValuePtr
                                .inferType(builder)
                                .unbox(builder.context())
                                .toType(builder.context())
                        ),
                        0
                    );
            }
            if (
                baseType.unbox(builder.context()) instanceof
                    AstType.Struct struct
            ) {
                return builder
                    .codeBuilder()
                    .getElementPtr(
                        struct.toType(builder.context()),
                        builder.loadObjPtrFromWrapper(
                            builder
                                .codeBuilder()
                                .load(
                                    Type.ptr(),
                                    baseValuePtr.compilePath(builder)
                                )
                        ),
                        Constant.integer(0).typed(Type.integer(32)),
                        Constant.integer(ptrOffset(builder)).typed(
                            Type.integer(32)
                        )
                    );
            }
            throw new SpannedException(
                this.span(),
                new SpannedException.ErrorType.DoesNotSupportField(
                    baseType,
                    this.identifier
                )
            );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            var baseType = baseValuePtr.inferType(builder);

            if (
                baseType.unbox(builder.context()) instanceof AstType.Array _ &&
                identifier.equals("length")
            ) {
                return new AstType.Boxed(new AstType.Integer(64, this.span));
            }
            if (
                baseType.unbox(builder.context()) instanceof
                    AstType.Struct(
                        List<Header.Parameter> parameters,
                        SpanData span
                    )
            ) {
                for (var param : parameters) {
                    if (param.name().equals(identifier)) {
                        return param.type();
                    }
                }
                throw new SpannedException(
                    span,
                    new SpannedException.ErrorType.DoesNotSupportField(
                        baseType,
                        this.identifier
                    )
                );
            }
            throw new SpannedException(
                span,
                new SpannedException.ErrorType.DoesNotSupportField(
                    baseType,
                    this.identifier
                )
            );
        }

        public int ptrOffset(CodeGenerator builder) {
            var baseType = baseValuePtr.inferType(builder);
            if (
                baseType.unbox(builder.context()) instanceof
                    AstType.Struct(
                        List<Header.Parameter> parameters,
                        SpanData span
                    )
            ) {
                int o = 0;
                for (var param : parameters) {
                    if (param.name().equals(identifier)) {
                        return o;
                    }
                    o += 1;
                }
                throw new SpannedException(
                    span,
                    new SpannedException.ErrorType.DoesNotSupportField(
                        baseType,
                        this.identifier
                    )
                );
            }
            throw new SpannedException(
                span,
                new SpannedException.ErrorType.DoesNotSupportField(
                    baseType,
                    this.identifier
                )
            );
        }
    }

    record Subscript(
        Expression baseArrayStackPtr,
        Expression subValue,
        SpanData span
    ) implements Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            return builder
                .codeBuilder()
                .load(
                    this.inferType(builder).toType(builder.context()),
                    this.compilePath(builder)
                );
        }

        @Override
        public Value compileInnerPath(CodeGenerator builder) {
            var type = baseArrayStackPtr.inferType(builder);
            if (
                type.unbox(builder.context()) instanceof
                    AstType.Array(AstType elementType, SpanData _)
            ) {
                var loadedFromOriginal = builder
                    .codeBuilder()
                    .load(Type.ptr(), baseArrayStackPtr.compilePath(builder));
                var objPtrFromWrapper = builder.loadObjPtrFromWrapper(
                    loadedFromOriginal
                );
                var objectAsValue = builder
                    .codeBuilder()
                    .load(
                        baseArrayStackPtr
                            .inferType(builder)
                            .unbox(builder.context())
                            .toType(builder.context()),
                        objPtrFromWrapper
                    );
                var arrayPtrFromObjectValue = builder
                    .codeBuilder()
                    .extractValue(
                        objectAsValue.typed(
                            baseArrayStackPtr
                                .inferType(builder)
                                .unbox(builder.context())
                                .toType(builder.context())
                        ),
                        1
                    );
                return builder.ptrToArrayElement(
                    elementType.toType(builder.context()),
                    arrayPtrFromObjectValue,
                    subValue
                );
            }

            throw new SpannedException(
                span,
                new SpannedException.ErrorType.DoesNotSupportSubscripting(type)
            );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            var baseType = baseArrayStackPtr.inferType(builder);
            if (
                baseType.unbox(builder.context()) instanceof
                    AstType.Array(AstType element, SpanData _)
            ) {
                return element;
            }

            throw new SpannedException(
                this.span(),
                new SpannedException.ErrorType.DoesNotSupportSubscripting(
                    baseType
                )
            );
        }
    }

    record StructLiteral(
        List<StructLiteral.Field> fields,
        SpanData span
    ) implements Expression {
        public record Field(String name, AstType type, Expression value) {}

        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            var llvmStructType = this.inferType(builder).toType(
                builder.context()
            );
            Value structValue = Constant.undef();
            for (int i = 0; i < fields.size(); i++) {
                var fieldValue = fields.get(i).value.compileValue(builder);
                var fieldType = fields.get(i).type.toType(builder.context());
                structValue = builder
                    .codeBuilder()
                    .insertValue(
                        structValue.typed(llvmStructType),
                        fieldValue.typed(fieldType),
                        i
                    );
            }
            return structValue;
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.Struct(
                this.fields.stream()
                    .map(x -> new Header.Parameter(x.name(), x.type()))
                    .toList(),
                this.span()
            );
        }
    }

    record ArrayLiteral(List<Expression> fields, SpanData span) implements
        Expression {
        @Override
        public Value compileInnerValue(CodeGenerator builder) {
            var llvmArrayType = this.inferType(builder).toType(
                builder.context()
            );
            var llvmElementType = this.inferElementType(builder).toType(
                builder.context()
            );
            var arrayObjectPtr = builder
                .codeBuilder()
                .callTyped(
                    Identifier.global("malloc").typed(
                        Type.function(Type.ptr(), List.of(Type.integer(64)))
                    ),
                    List.of(
                        Constant.integer(fields.size() * 8L).typed(
                            Type.integer(64)
                        )
                    )
                );

            int i = 0;
            for (var field : fields) {
                builder
                    .codeBuilder()
                    .store(
                        field.compileValue(builder).typed(llvmElementType),
                        builder.ptrToArrayElement(
                            llvmElementType,
                            arrayObjectPtr,
                            new Integer(i, field.span())
                        )
                    );
                i++;
            }

            return builder
                .codeBuilder()
                .insertValue(
                    builder
                        .codeBuilder()
                        .insertValue(
                            Constant.undef().typed(llvmArrayType),
                            Constant.integer(fields.size()).typed(
                                Type.integer(64)
                            ),
                            0
                        )
                        .typed(llvmArrayType),
                    arrayObjectPtr.typed(Type.ptr()),
                    1
                );
        }

        @Override
        public AstType inferType(CodeGenerator builder) {
            return new AstType.Array(
                this.inferElementType(builder),
                this.span()
            );
        }

        public AstType inferElementType(CodeGenerator builder) {
            try {
                return this.fields.getFirst().inferType(builder);
            } catch (Exception e) {
                return new AstType.Any(this.span());
            }
        }
    }
}
