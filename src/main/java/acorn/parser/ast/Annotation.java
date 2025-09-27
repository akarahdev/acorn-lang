package acorn.parser.ast;

import java.util.List;

public record Annotation(String name, List<Expression> values) {}
