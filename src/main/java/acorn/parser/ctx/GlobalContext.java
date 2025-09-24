package acorn.parser.ctx;

import acorn.parser.ast.AstType;

import java.util.HashMap;
import java.util.Map;

public record GlobalContext(
        Map<String, FunctionRecord> functions,
        Map<String, AstType> typeAliases
) {
    public static GlobalContext create() {
        return new GlobalContext(
                new HashMap<>(),
                new HashMap<>()
        );
    }
}
