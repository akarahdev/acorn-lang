package acorn.parser;

import acorn.parser.ast.AstType;
import acorn.parser.ast.Expression;
import acorn.parser.ast.Header;
import acorn.parser.ast.Statement;
import acorn.reader.Reader;
import acorn.token.Token;

import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;

public class Parser {
    Reader<List<Token>, Token> reader;

    public static Parser create(List<Token> tokens) {
        var p = new Parser();
        p.reader = Reader.create(tokens, List::get, List::size);
        return p;
    }

    public Header.Function parseFunction() {
        this.reader.expect(Token.FnKeyword.class);

        var name = this.reader.expect(Token.Identifier.class);
        this.reader.expect(Token.OpenParen.class);
        this.reader.expect(Token.CloseParen.class);
        this.reader.expect(Token.RightArrow.class);
        var returnType = this.parseType();
        var body = this.parseBody();
        return new Header.Function(
                name.name(),
                returnType,
                body
        );
    }

    public List<Statement> parseBody() {
        this.reader.expect(Token.OpenBrace.class);
        var stmts = new ArrayList<Statement>();
        while(!(this.reader.peek() instanceof Token.CloseBrace)) {
            stmts.add(this.parseStatement());
        }
        this.reader.expect(Token.CloseBrace.class);
        return stmts;
    }

    public Statement parseStatement() {
        switch (this.reader.peek()) {
            case Token.ReturnKeyword returnKeyword -> {
                this.reader.next();
                return new Statement.Ret(this.parseExpression());
            }
            default -> new Statement.Dropping(this.parseExpression());
        }
        return null;
    }

    public Expression parseExpression() {
        return parseTerm();
    }

    public Expression parseTerm() {
        var expr = parseBaseValue();
        while(reader.peek() instanceof Token.Plus) {
            reader.expect(Token.Plus.class);
            expr = new Expression.Addition(expr, parseBaseValue());
        }
        return expr;
    }

    public Expression parseBaseValue() {
        var n = this.reader.next();
        return switch (n) {
            case Token.Integer integer -> new Expression.Integer(integer.value());
            default -> throw new RuntimeException("Invalid base value " + n);
        };
    }

    public AstType parseType() {
        var name = this.reader.expect(Token.Identifier.class);
        if(name.name().startsWith("i")) {
            var replaced = name.name().replace("i", "");
            try {
                var bits = Integer.parseInt(replaced);
                return new AstType.Integer(bits);
            } catch (Exception ignored) {

            }
        }
        throw new RuntimeException("Invalid type name: " + name.name());
    }
}
