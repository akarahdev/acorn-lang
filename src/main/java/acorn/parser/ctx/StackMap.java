package acorn.parser.ctx;

import acorn.parser.ast.AstType;
import llvm4j.module.value.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record StackMap(
    List<Frame> stackFrames
) {
    public record VariableData(
            AstType type,
            Value stackSlot
    ) {

    }
    public record Frame(
            Map<String, VariableData> localVariables
    ) {}

    public void pushFrame() {
        this.stackFrames.add(new Frame(new HashMap<>()));
    }

    public Frame popFrame() {
        return this.stackFrames.removeLast();
    }

    public void storeVariable(String name, Value allocaPtr, AstType varType) {
        try {
            var previousType = this.getLocalVariable(name);
            assert previousType.type().equals(varType);
        } catch (Exception e) {
            this.stackFrames.getLast().localVariables().put(name, new VariableData(varType, allocaPtr));
        }
    }

    public boolean hasLocalVariable(String name) {
        for(var frame : this.stackFrames) {
            if(frame.localVariables.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    /// @throws RuntimeException If the variable is not defined
    public VariableData getLocalVariable(String name) {
        for(var frame : this.stackFrames) {
            if(frame.localVariables.containsKey(name)) {
                return frame.localVariables.get(name);
            }
        }
        throw new RuntimeException("Unable to infer type of local variable " + name);
    }
}
