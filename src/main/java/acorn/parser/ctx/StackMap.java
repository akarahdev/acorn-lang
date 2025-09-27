package acorn.parser.ctx;

import acorn.parser.ast.AstType;
import acorn.token.SpanData;
import acorn.token.SpannedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import llvm4j.module.value.Value;

public record StackMap(List<Frame> stackFrames) {
    public record VariableData(AstType type, Value stackSlot) {}

    public record Frame(Map<String, VariableData> localVariables) {}

    public void pushFrame() {
        this.stackFrames.add(new Frame(new HashMap<>()));
    }

    public Frame popFrame() {
        return this.stackFrames.removeLast();
    }

    public void storeVariable(
        String name,
        Value allocaPtr,
        AstType varType,
        SpanData span
    ) {
        if (this.hasLocalVariable(name)) {
            var previousType = this.getLocalVariable(name, span);
            if (!previousType.type().typeEquals(varType)) {
                throw new SpannedException(
                    span,
                    new SpannedException.ErrorType.WrongType(
                        List.of(previousType.type()),
                        varType
                    )
                );
            }
        } else {
            this.stackFrames.getLast()
                .localVariables()
                .put(name, new VariableData(varType, allocaPtr));
        }
    }

    public boolean hasLocalVariable(String name) {
        for (var frame : this.stackFrames) {
            if (frame.localVariables.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    /// @throws RuntimeException If the variable is not defined
    public VariableData getLocalVariable(String name, SpanData span) {
        for (var frame : this.stackFrames) {
            if (frame.localVariables.containsKey(name)) {
                return frame.localVariables.get(name);
            }
        }

        throw new SpannedException(
            span,
            new SpannedException.ErrorType.VariableDoesNotExist(name)
        );
    }
}
