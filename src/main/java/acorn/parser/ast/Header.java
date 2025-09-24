package acorn.parser.ast;

import acorn.parser.CodeGenerator;
import llvm4j.module.Module;
import llvm4j.module.type.Type;
import llvm4j.module.value.Identifier;

import java.util.List;

public sealed interface Header {
    void emit(Module.Builder builder);

    record Function(String name, AstType returnType, List<Statement> statements) implements Header {
        @Override
        public void emit(Module.Builder builder) {
            builder.withFunction(
                    Identifier.global(this.name.replace("::", "__")),
                    fb -> fb.withReturnType(this.returnType.toType())
                            .withCode(bb -> {
                                var cg = new CodeGenerator(bb);
                                for(var statement : this.statements) {
                                    statement.compile(cg);
                                }
                                return bb;
                            })
            );
        }
    }
}
