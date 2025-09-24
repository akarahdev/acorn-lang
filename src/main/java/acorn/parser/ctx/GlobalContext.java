package acorn.parser.ctx;

import llvm4j.module.value.Identifier;

import java.util.HashMap;
import java.util.Map;

public record GlobalContext(
        Map<String, FunctionRecord> functions
) {
    public static GlobalContext create() {
        return new GlobalContext(
                new HashMap<>()
        );
    }
}
