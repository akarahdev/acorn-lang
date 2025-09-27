package acorn.ui;

import acorn.Main;
import acorn.parser.Parser;
import acorn.parser.ast.Header;
import acorn.parser.ctx.GlobalContext;
import acorn.token.Tokenizer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import llvm4j.module.Module;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "build", description = "Build the project")
public class CLI implements Runnable {

    @Parameters(index = "0", defaultValue = "run")
    public Mode mode = Mode.build;

    List<Header> headers;
    Path outFile;

    enum Mode {
        check,
        build,
        run;

        public int compilationHierarchy() {
            return switch (this) {
                case Mode.check -> 0;
                case Mode.build -> 1;
                case Mode.run -> 2;
            };
        }
    }

    @Override
    public void run() {
        try {
            if (!Files.exists(Path.of("./build/"))) {
                Files.createDirectories(Path.of("./build"));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (this.mode.compilationHierarchy() >= 0) {
            this.headers = getHeaders();
        }
        if (this.mode.compilationHierarchy() >= 1) {
            this.outFile = this.compileToOutFile();
        }
        if (this.mode.compilationHierarchy() >= 2) {
            this.executeOutFile();
        }
    }

    public List<Header> getHeaders() {
        try {
            var contents = new StringBuilder();
            contents.append(this.loadStdlib());

            Files.walk(Path.of("./src/"))
                .filter(Files::isRegularFile)
                .filter(x -> x.toString().endsWith(".acorn"))
                .forEach(path -> {
                    try {
                        contents.append("\n\n").append(Files.readString(path));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

            var tokens = Tokenizer.create(contents.toString()).tokenize();
            System.out.println(tokens);

            var parser = Parser.create(tokens);
            var functions = parser.parseHeaders();

            return functions;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Path compileToOutFile() {
        var ctx = GlobalContext.create();
        var module = Module.builder();
        System.out.println(this.headers);
        this.headers.forEach(x -> x.preprocess(ctx));
        this.headers.forEach(x -> x.emit(module, ctx));

        try {
            var outFile = Paths.get("./build/output.ll");
            module.build().emit(outFile);
            return outFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void executeOutFile() {
        try {
            var p1 = Runtime.getRuntime().exec(
                new String[] {
                    "clang",
                    this.outFile.toString(),
                    "-o",
                    "./build/a.out",
                }
            );
            p1.getInputStream().transferTo(System.out);
            p1.getErrorStream().transferTo(System.err);
            p1.waitFor();

            var p2 = Runtime.getRuntime().exec(
                new String[] { "./build/a.out" }
            );
            p2.getInputStream().transferTo(System.out);
            p2.getErrorStream().transferTo(System.err);
            var returns = p2.waitFor();
            System.out.println("Exited with code " + returns);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String loadStdlib() throws URISyntaxException, IOException {
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
