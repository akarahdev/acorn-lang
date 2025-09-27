package acorn;

import acorn.parser.Parser;
import acorn.parser.ctx.GlobalContext;
import acorn.token.Tokenizer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Objects;
import llvm4j.module.Module;

public class Main {
    public static void main(String[] args)
        throws IOException, InterruptedException, URISyntaxException {
        var stdlib = loadStdlib();

        var sourceFilePath = args[0];
        var contents =
            stdlib + "\n" + Files.readString(Paths.get(sourceFilePath));

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

        var p1 = Runtime.getRuntime().exec(
            new String[] { "clang", "./build/out.ll", "-o", "./build/a.out" }
        );
        p1.getInputStream().transferTo(System.out);
        p1.getErrorStream().transferTo(System.err);
        p1.waitFor();

        var p2 = Runtime.getRuntime().exec(new String[] { "./build/a.out" });
        p2.getInputStream().transferTo(System.out);
        p2.getErrorStream().transferTo(System.err);
        var returns = p2.waitFor();
        System.out.println("Exited with code " + returns);
    }

    static String loadStdlib() throws URISyntaxException, IOException {
        var sb = new StringBuilder();
        var files = new String[] { "/std/entrypoint.acorn", "/std/libc.acorn" };
        for (var file : files) {
            sb
                .append(
                    Files.readString(
                        Paths.get(
                            Objects.requireNonNull(
                                Main.class.getResource(file)
                            ).toURI()
                        )
                    )
                )
                .append("\n\n");
        }
        return sb.toString();
    }
}
