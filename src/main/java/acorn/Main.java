package acorn;

import acorn.parser.ctx.GlobalContext;
import acorn.parser.Parser;
import acorn.token.Tokenizer;
import llvm4j.module.Module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    static void main(String[] args) throws IOException, InterruptedException {
        var sourceFilePath = args[0];
        var contents = Files.readString(Paths.get(sourceFilePath));

        var tokens = Tokenizer.create(contents).tokenize();
        System.out.println(tokens);

        var parser = Parser.create(tokens);
        var functions = parser.parseHeaders();

        var ctx = GlobalContext.create();
        var module = Module.builder();
        System.out.println(functions);
        functions.forEach(x -> x.preprocess(ctx));
        functions.forEach(x -> x.emit(module, ctx));
        module.build().emit(Paths.get("./build/out.ll"));

        var p1 = Runtime.getRuntime().exec(new String[] { "clang", "./build/out.ll", "-o", "./build/a.out" });
        p1.getInputStream().transferTo(System.out);
        p1.getErrorStream().transferTo(System.err);
        p1.waitFor();

        var p2 = Runtime.getRuntime().exec(new String[] { "./build/a.out" });
        p2.getInputStream().transferTo(System.out);
        p2.getErrorStream().transferTo(System.err);
        var returns = p2.waitFor();
        System.out.println("Exited with code " + returns);
    }
}
