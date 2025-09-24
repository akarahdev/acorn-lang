package acorn.parser;

import acorn.parser.ctx.GlobalContext;
import llvm4j.module.Function;
import llvm4j.module.Module;
import llvm4j.module.code.BasicBlock;
import llvm4j.module.type.Type;
import llvm4j.module.value.Constant;
import llvm4j.module.value.Identifier;
import llvm4j.module.value.Value;

import java.util.List;

public record CodeGenerator(
        GlobalContext context,
        Module.Builder module,
        Function.Builder function,
        BasicBlock.Builder codeBuilder
) {
    public record WrappedStructure(
            Value wrapperPtr,
            Value objPtr
    ) {}

    public WrappedStructure allocateStructure(int dataSize) {
        var objPtr = this.codeBuilder().call(
                Identifier.global("malloc").typed(Type.function(Type.ptr(), List.of(Type.integer(32)))),
                List.of(
                        Constant.integer(dataSize).typed(Type.integer(32))
                )
        );
        var wrapperPtr = this.codeBuilder().call(
                Identifier.global("malloc").typed(Type.function(Type.ptr(), List.of(Type.integer(32)))),
                List.of(
                        Constant.integer(16).typed(Type.integer(32))
                )
        );
        var wrapperType = Type.struct(List.of(
                Type.integer(4),
                Type.integer(4),
                Type.ptr()
        ));
        this.codeBuilder.store(
                Constant.undef().typed(wrapperType),
                wrapperPtr
        );
        this.codeBuilder.store(
                Constant.integer(1).typed(Type.integer(32)),
                this.codeBuilder.getElementPtr(wrapperType, wrapperPtr, Constant.integer(0).typed(Type.integer(32)))
        );
        this.codeBuilder.store(
                Constant.integer(1).typed(Type.integer(32)),
                this.codeBuilder.getElementPtr(wrapperType, wrapperPtr, Constant.integer(1).typed(Type.integer(32)))
        );
        this.codeBuilder.store(
                wrapperPtr.typed(Type.ptr()),
                this.codeBuilder.getElementPtr(wrapperType, wrapperPtr, Constant.integer(2).typed(Type.integer(32)))
        );
        return new WrappedStructure(wrapperPtr, objPtr);
    }
}
