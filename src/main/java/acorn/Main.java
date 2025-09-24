package acorn;

import acorn.token.Tokenizer;

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
    }
}
