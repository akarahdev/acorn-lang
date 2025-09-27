package acorn.parser.ctx;

import acorn.parser.ast.AstType;
import acorn.parser.ast.Header;

public record FunctionRecord(
    String mangling,
    boolean varargs,
    Header.Function function
) {
    public AstType ptrType() {
        return new AstType.Function(
            function.returnType(),
            function.parameters().stream().map(Header.Parameter::type).toList(),
            varargs
        );
    }
}
