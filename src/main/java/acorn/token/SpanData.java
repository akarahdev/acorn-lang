package acorn.token;

import java.util.HashMap;
import java.util.Map;

public record SpanData(ColumnAndRow location, String fileName) {
    public static Map<String, String> FILE_MAPS = new HashMap<>();

    public record ColumnAndRow(int column, int row) {}
}
