package acorn.token;

import acorn.reader.Reader;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {
    Reader<String, Character> stringReader;
    List<Token> tokens = new ArrayList<>();

    public static Tokenizer create(String source) {
        var t = new Tokenizer();
        t.stringReader = Reader.create(source + "\n\n\n", String::charAt, String::length);
        return t;
    }

    public List<Token> tokenize() {
        while(stringReader.hasNext()) {
            try {
                var token = tokenizeOnce();
                if(token != null) {
                    this.tokens.add(token);
                }
            } catch (Exception ignored) {
                break;
            }
        }
        return this.tokens;
    }

    public Token tokenizeOnce() {
        if(!stringReader.hasNext()) {
            return null;
        }

        this.skipWhitespace();

        if(stringReader.peek() == 'c' && stringReader.peek(1) == '"') {
            stringReader.next();
            stringReader.next();

            var sb = new StringBuilder();
            while(stringReader.peek() != '"') {
                sb.append(stringReader.next());
            }
            stringReader.next();

            return new Token.CString(sb.toString());
        }

        // tokenize identifiers
        if(Character.isJavaIdentifierStart(stringReader.peek())) {
            var sb = new StringBuilder();
            while(Character.isJavaIdentifierPart(stringReader.peek()) || stringReader.peek() == ':') {
                sb.append(stringReader.next());
            }
            var str = sb.toString();
            return switch (str) {
                case "fn" -> new Token.FnKeyword();
                case "return" -> new Token.ReturnKeyword();
                case "type" -> new Token.TypeKeyword();
                case "box" -> new Token.BoxKeyword();
                case "unbox" -> new Token.UnboxKeyword();
                default -> new Token.Identifier(sb.toString());
            };
        }

        // tokenize numbers
        if(Character.isDigit(stringReader.peek())) {
            var sb = new StringBuilder();
            while(Character.isDigit(stringReader.peek()) || stringReader.peek() == '.') {
                sb.append(stringReader.next());
            }
            var str = sb.toString();
            if(str.contains(".")) {
                return new Token.Floating(Double.parseDouble(str));
            } else {
                return new Token.Integer(Integer.parseInt(str));
            }
        }

        if(stringReader.peek() == '"') {
            var sb = new StringBuilder();
            stringReader.next();
            while(stringReader.peek() != '"') {
                sb.append(stringReader.next());
            }
            stringReader.next();

            return new Token.String(sb.toString());
        }

        // parse misc symbols
        return switch (stringReader.next()) {
            case '{' -> new Token.OpenBrace();
            case '}' -> new Token.CloseBrace();
            case '(' -> new Token.OpenParen();
            case ')' -> new Token.CloseParen();
            case '+' -> new Token.Plus();
            case '-' -> switch (stringReader.peek()) {
                case '>' -> new Token.RightArrow();
                default -> new Token.Minus();
            };
            case '*' -> new Token.Star();
            case '/' -> new Token.Slash();
            case '@' -> new Token.At();
            case ',' -> new Token.Comma();
            case '=' -> new Token.Equals();
            default -> null;
        };
    }

    public void skipWhitespace() {
        var ch = stringReader.peek();
        while(ch != null && Character.isWhitespace(ch)) {
            stringReader.next();
            ch = stringReader.peek();
        }
    }
}
