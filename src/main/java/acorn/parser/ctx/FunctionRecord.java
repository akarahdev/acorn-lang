package acorn.parser.ctx;

import acorn.parser.ast.AstType;
import acorn.parser.ast.Header;
import acorn.token.SpanData;

public record FunctionRecord(
    String mangling,
    boolean varargs,
    Header.Function function,
    SpanData span
) {
    public AstType ptrType() {
        return new AstType.Function(
            function.returnType(),
            function.parameters().stream().map(Header.Parameter::type).toList(),
            varargs,
            span
        );
    }
}
