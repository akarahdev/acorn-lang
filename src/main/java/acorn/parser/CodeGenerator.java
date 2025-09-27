package acorn.parser;

import acorn.parser.ast.Expression;
import acorn.parser.ctx.GlobalContext;
import acorn.parser.ctx.StackMap;
import java.util.List;
import llvm4j.module.Function;
import llvm4j.module.Module;
import llvm4j.module.code.BasicBlock;
import llvm4j.module.type.Type;
import llvm4j.module.value.Constant;
import llvm4j.module.value.Identifier;
import llvm4j.module.value.TypeValuePair;
import llvm4j.module.value.Value;

public record CodeGenerator(
    GlobalContext context,
    Module.Builder module,
    Function.Builder function,
    BasicBlock.Builder codeBuilder,
    StackMap stackMap
) {
    public static Type REF_COUNT_WRAPPER = Type.struct(
        List.of(Type.integer(32), Type.integer(32), Type.ptr())
    );

    public Value wrapValueInRefCount(TypeValuePair value, int dataSize) {
        var objPtr = this.codeBuilder().callTyped(
            Identifier.global("malloc").typed(
                Type.function(Type.ptr(), List.of(Type.integer(64)))
            ),
            List.of(Constant.integer(dataSize).typed(Type.integer(64)))
        );
        this.codeBuilder.store(value, objPtr);
        var wrapperPtr = this.codeBuilder().callTyped(
            Identifier.global("malloc").typed(
                Type.function(Type.ptr(), List.of(Type.integer(64)))
            ),
            List.of(Constant.integer(128).typed(Type.integer(64)))
        );
        this.codeBuilder.store(
            Constant.undef().typed(REF_COUNT_WRAPPER),
            wrapperPtr
        );
        this.codeBuilder.store(
            Constant.integer(1).typed(Type.integer(32)),
            this.codeBuilder.getElementPtr(
                REF_COUNT_WRAPPER,
                wrapperPtr,
                Constant.integer(0).typed(Type.integer(32)),
                Constant.integer(0).typed(Type.integer(32))
            )
        );
        this.codeBuilder.store(
            Constant.integer(1).typed(Type.integer(32)),
            this.codeBuilder.getElementPtr(
                REF_COUNT_WRAPPER,
                wrapperPtr,
                Constant.integer(0).typed(Type.integer(32)),
                Constant.integer(1).typed(Type.integer(32))
            )
        );
        this.codeBuilder.store(
            objPtr.typed(Type.ptr()),
            this.codeBuilder.getElementPtr(
                REF_COUNT_WRAPPER,
                wrapperPtr,
                Constant.integer(0).typed(Type.integer(32)),
                Constant.integer(2).typed(Type.integer(32))
            )
        );
        return wrapperPtr;
    }

    public Value loadObjPtrFromWrapper(Value wrapperPtr) {
        this.codeBuilder.comment(
            "Loading ref count ptr from " + wrapperPtr.toString()
        );
        var o = this.codeBuilder.load(
            Type.ptr(),
            this.codeBuilder.getElementPtr(
                REF_COUNT_WRAPPER,
                wrapperPtr,
                Constant.integer(0).typed(Type.integer(32)),
                Constant.integer(2).typed(Type.integer(32))
            )
        );
        this.codeBuilder.comment(
            "Finishing loading ref count ptr from " + wrapperPtr.toString()
        );
        return o;
    }

    public Value loadValueFromRefCount(Type expectedType, Value wrapperPtr) {
        this.codeBuilder.comment(
            "Loading ref count value from " + wrapperPtr.toString()
        );
        var o = this.codeBuilder.load(
            expectedType,
            loadObjPtrFromWrapper(wrapperPtr)
        );
        this.codeBuilder.comment(
            "Stopping loading ref count value from " + wrapperPtr.toString()
        );
        return o;
    }

    public Value ptrToArrayElement(
        Type elementType,
        Value arrayPtr,
        Expression offset
    ) {
        return this.codeBuilder.getElementPtr(
            Type.array(0, elementType),
            arrayPtr,
            Constant.integer(0).typed(Type.integer(32)),
            offset
                .compileValue(this)
                .typed(offset.inferType(this).toType(this.context))
        );
    }
}
