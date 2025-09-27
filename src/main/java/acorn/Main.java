package acorn;

import acorn.ui.CLI;
import java.io.IOException;
import java.net.URISyntaxException;
import picocli.CommandLine;

public class Main {

    public static void main(String[] args)
        throws IOException, InterruptedException, URISyntaxException {
        new CommandLine(new CLI()).execute(args);
    }
}
