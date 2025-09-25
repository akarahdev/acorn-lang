package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import acorn.parser.ctx.FunctionRecord;
import acorn.parser.ctx.GlobalContext;
import acorn.parser.ctx.StructRecord;
import llvm4j.module.Module;
import llvm4j.module.value.Identifier;

import java.util.List;

public sealed interface Header {
    void preprocess(GlobalContext context);
    void emit(Module.Builder builder, GlobalContext context);

    record Parameter(String name, AstType type) {}

    record TypeAlias(String name, AstType type, List<Annotation> annotations) implements Header {
        @Override
        public void preprocess(GlobalContext context) {
            context.typeAliases().put(
                    name,
                    this.type
            );
        }

        @Override
        public void emit(Module.Builder builder, GlobalContext context) {

        }
    }

    record Function(String name, AstType returnType, List<Parameter> parameters, List<Statement> statements, List<Annotation> annotations) implements Header {
        @Override
        public void preprocess(GlobalContext context) {
            boolean varargs = false;
            String mangling = this.name.replace("::", "__");

            for(var annotation : this.annotations) {
                switch (annotation.name()) {
                    case "varargs" -> varargs = true;
                    case "mangle_as" -> mangling = ((Expression.StringValue) annotation.values().getFirst()).value();
                }
            }
            boolean finalVarargs = varargs;

            context.functions().put(this.name, new FunctionRecord(mangling, finalVarargs, this));
        }

        @Override
        public void emit(Module.Builder builder, GlobalContext context) {
            var varargs = context.functions().get(this.name).varargs();
            var mangling = context.functions().get(this.name).mangling();

            builder.withFunction(
                    Identifier.global(mangling),
                    fb -> {
                        fb.withReturnType(this.returnType.toType(context));
                        if(varargs) {
                            fb.withVarargs();
                        }
                        for(var parameter : this.parameters) {
                            fb.withParameter(Identifier.local(parameter.name()).parameterized(parameter.type().toType(context)));
                        }
                        if(statements != null) {
                            fb.withCode(bb -> {
                                var cg = new CodeGenerator(context, builder, fb, bb);
                                for(var statement : this.statements) {
                                    statement.compile(cg);
                                }
                                return bb;
                            });
                        }
                        return fb;
                    }
            );
        }
    }
}
