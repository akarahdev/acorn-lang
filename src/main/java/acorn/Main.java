package acorn;

import acorn.parser.Parser;
import acorn.token.Tokenizer;
import llvm4j.module.Module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    static void main(String[] args) throws IOException {
        var sourceFilePath = args[0];
        var contents = Files.readString(Paths.get(sourceFilePath));
        IO.println(contents);

        var tokens = Tokenizer.create(contents).tokenize();
        IO.println(tokens);

        var parser = Parser.create(tokens);
        var function = parser.parseFunction();
        IO.println(function);

        var module = Module.builder();
        function.emit(module);
        module.build().emit(Paths.get("./build/out.ll"));
    }
}
