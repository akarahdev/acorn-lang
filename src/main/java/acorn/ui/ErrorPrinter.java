package acorn.ui;

import acorn.token.SpannedException;
import java.util.HashMap;
import java.util.Map;

public class ErrorPrinter {

    public static Map<String, String> FILE_CONTENTS = new HashMap<>();

    public static void print(SpannedException e) {
        System.out.println(e.error().message());

        var line = FILE_CONTENTS.get(e.getSpan().fileName())
            .lines()
            .skip(e.getSpan().location().row())
            .findFirst()
            .orElseThrow();
        System.out.println(line);
        System.out.println(
            " ".repeat(e.getSpan().location().column() - 1) + "^"
        );
        System.out.println(
            e.getSpan().fileName().trim() + " @ " + e.getSpan().location().row()
        );
    }
}
