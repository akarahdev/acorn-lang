package acorn.parser.ctx;

import acorn.parser.ast.AstType;
import acorn.parser.ast.Header;

public record StructRecord(
    Header.TypeAlias structHeader,
    AstType.Struct structType
) {}
