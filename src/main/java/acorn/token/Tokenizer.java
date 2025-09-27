package acorn.token;

import acorn.reader.Reader;
import acorn.token.SpanData.ColumnAndRow;
import acorn.ui.ErrorPrinter;
import java.util.ArrayList;
import java.util.List;

public class Tokenizer {

    Reader<String, Character> stringReader;
    String fileName;
    List<Token> tokens = new ArrayList<>();

    public static Tokenizer create(String source, String fileName) {
        ErrorPrinter.FILE_CONTENTS.put(fileName, source);
        var t = new Tokenizer();
        t.fileName = fileName;
        t.stringReader = Reader.create(
            source + "\n\n\n",
            String::charAt,
            String::length,
            (_, _) -> {}
        );
        return t;
    }

    public List<Token> tokenize() {
        while (stringReader.hasNext()) {
            try {
                var token = tokenizeOnce();
                if (token != null) {
                    this.tokens.add(token);
                }
            } catch (Exception ignored) {
                break;
            }
        }
        return this.tokens;
    }

    public Token tokenizeOnce() {
        if (!stringReader.hasNext()) {
            return null;
        }

        this.skipWhitespace();

        if (stringReader.peek() == 'c' && stringReader.peek(1) == '"') {
            stringReader.next();
            stringReader.next();

            var sb = new StringBuilder();
            while (stringReader.peek() != '"') {
                sb.append(stringReader.next());
            }
            stringReader.next();

            return new Token.CString(sb.toString(), this.createSpanData());
        }

        // tokenize identifiers
        if (Character.isJavaIdentifierStart(stringReader.peek())) {
            var sb = new StringBuilder();
            while (
                Character.isJavaIdentifierPart(stringReader.peek()) ||
                stringReader.peek() == ':'
            ) {
                sb.append(stringReader.next());
            }
            var str = sb.toString();
            return switch (str) {
                case "fn" -> new Token.FnKeyword(this.createSpanData());
                case "return" -> new Token.ReturnKeyword(this.createSpanData());
                case "type" -> new Token.TypeKeyword(this.createSpanData());
                case "box" -> new Token.BoxKeyword(this.createSpanData());
                case "unbox" -> new Token.UnboxKeyword(this.createSpanData());
                case "struct" -> new Token.StructKeyword(this.createSpanData());
                default -> new Token.Identifier(
                    sb.toString(),
                    this.createSpanData()
                );
            };
        }

        // tokenize numbers
        if (Character.isDigit(stringReader.peek())) {
            var sb = new StringBuilder();
            while (
                Character.isDigit(stringReader.peek()) ||
                stringReader.peek() == '.'
            ) {
                sb.append(stringReader.next());
            }
            var str = sb.toString();
            if (str.contains(".")) {
                return new Token.Floating(
                    Double.parseDouble(str),
                    this.createSpanData()
                );
            } else {
                return new Token.Integer(
                    Integer.parseInt(str),
                    this.createSpanData()
                );
            }
        }

        if (stringReader.peek() == '"') {
            var sb = new StringBuilder();
            stringReader.next();
            while (stringReader.peek() != '"') {
                sb.append(stringReader.next());
            }
            stringReader.next();

            return new Token.String(sb.toString(), this.createSpanData());
        }

        // parse misc symbols
        return switch (stringReader.next()) {
            case '{' -> new Token.OpenBrace(this.createSpanData());
            case '}' -> new Token.CloseBrace(this.createSpanData());
            case '(' -> new Token.OpenParen(this.createSpanData());
            case ')' -> new Token.CloseParen(this.createSpanData());
            case '[' -> new Token.OpenBracket(this.createSpanData());
            case ']' -> new Token.CloseBracket(this.createSpanData());
            case '+' -> new Token.Plus(this.createSpanData());
            case '-' -> switch (stringReader.peek()) {
                case '>' -> new Token.RightArrow(this.createSpanData());
                default -> new Token.Minus(this.createSpanData());
            };
            case '*' -> new Token.Star(this.createSpanData());
            case '/' -> new Token.Slash(this.createSpanData());
            case '@' -> new Token.At(this.createSpanData());
            case ',' -> new Token.Comma(this.createSpanData());
            case '=' -> new Token.Equals(this.createSpanData());
            case '.' -> new Token.Period(this.createSpanData());
            default -> null;
        };
    }

    public void skipWhitespace() {
        var ch = stringReader.peek();
        while (ch != null && Character.isWhitespace(ch)) {
            stringReader.next();
            ch = stringReader.peek();
        }
    }

    public SpanData createSpanData() {
        return new SpanData(this.columnAndRow(), this.fileName);
    }

    public ColumnAndRow columnAndRow() {
        int column = 0;
        int row = 0;
        for (int i = 0; i < this.stringReader.index(); i++) {
            column += 1;
            if (this.stringReader.value().charAt(i) == '\n') {
                column = 0;
                row += 1;
            }
        }

        return new ColumnAndRow(column, row);
    }
}
