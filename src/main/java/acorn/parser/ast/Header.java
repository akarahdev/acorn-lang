package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import acorn.parser.ctx.FunctionRecord;
import acorn.parser.ctx.GlobalContext;
import acorn.parser.ctx.StackMap;
import acorn.token.SpanData;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import llvm4j.module.Module;
import llvm4j.module.value.Identifier;

public sealed interface Header {
    void preprocess(GlobalContext context);
    void emit(Module.Builder builder, GlobalContext context);

    record Parameter(String name, AstType type) {}

    record TypeAlias(
        String name,
        AstType type,
        List<Annotation> annotations,
        SpanData span
    ) implements Header {
        @Override
        public void preprocess(GlobalContext context) {
            context.typeAliases().put(name, this.type);
        }

        @Override
        public void emit(Module.Builder builder, GlobalContext context) {}
    }

    record Function(
        String name,
        AstType returnType,
        List<Parameter> parameters,
        List<Statement> statements,
        List<Annotation> annotations,
        SpanData span
    ) implements Header {
        public static String mangleSafely(String string) {
            return string
                .replace("(", "_op_")
                .replace(")", "_cl_")
                .replace(",", "_sep_")
                .replace(":", "_");
        }

        @Override
        public void preprocess(GlobalContext context) {
            boolean varargs = false;
            String mangling = mangleSafely(
                "acorn_coded::" +
                    this.name +
                    "(" +
                    this.parameters.stream()
                        .map(p -> p.type.typeName())
                        .collect(Collectors.joining(",")) +
                    ")::" +
                    this.returnType.typeName()
            );

            for (var annotation : this.annotations) {
                switch (annotation.name()) {
                    case "varargs" -> varargs = true;
                    case "mangle_as" -> mangling =
                        ((Expression.StringValue) annotation
                                .values()
                                .getFirst()).value();
                }
            }
            boolean finalVarargs = varargs;

            context
                .functions()
                .put(
                    this.name,
                    new FunctionRecord(mangling, finalVarargs, this, this.span)
                );
        }

        @Override
        public void emit(Module.Builder builder, GlobalContext context) {
            var varargs = context.functions().get(this.name).varargs();
            var mangling = context.functions().get(this.name).mangling();

            builder.withFunction(Identifier.global(mangling), fb -> {
                fb.withReturnType(this.returnType.toType(context));
                if (varargs) {
                    fb.withVarargs();
                }
                for (var parameter : this.parameters) {
                    fb.withParameter(
                        Identifier.local(parameter.name()).parameterized(
                            parameter.type().toType(context)
                        )
                    );
                }
                if (statements != null) {
                    fb.withCode(bb -> {
                        var sm = new StackMap(new ArrayList<>());
                        var cg = new CodeGenerator(
                            context,
                            builder,
                            fb,
                            bb,
                            sm
                        );

                        sm.pushFrame();

                        for (var parameter : this.parameters) {
                            var paramSlot = bb.alloca(
                                parameter.type().toType(context)
                            );
                            var paramValue = Identifier.local(parameter.name());
                            bb.store(
                                paramValue.typed(
                                    parameter.type().toType(context)
                                ),
                                paramSlot
                            );
                            sm.storeVariable(
                                parameter.name(),
                                paramSlot,
                                parameter.type(),
                                this.span
                            );
                        }
                        sm.pushFrame();
                        for (var statement : this.statements) {
                            statement.compile(cg);
                        }
                        sm.popFrame();
                        sm.popFrame();
                        return bb;
                    });
                }
                return fb;
            });
        }
    }
}
