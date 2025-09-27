package acorn;

import acorn.parser.Parser;
import acorn.parser.ctx.GlobalContext;
import acorn.token.Tokenizer;
import acorn.ui.CLI;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Objects;
import llvm4j.module.Module;
import picocli.CommandLine;

public class Main {

    public static void main(String[] args)
        throws IOException, InterruptedException, URISyntaxException {
        new CommandLine(new CLI()).execute(args);
    }
}
