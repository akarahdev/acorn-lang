package acorn.parser;

import acorn.parser.ast.*;
import acorn.reader.Reader;
import acorn.token.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Parser {
    Reader<List<Token>, Token> reader;

    public static Parser create(List<Token> tokens) {
        var p = new Parser();
        p.reader = Reader.create(tokens, List::get, List::size);
        return p;
    }

    public List<Annotation> parseAnnotations() {
        var list = new ArrayList<Annotation>();

        while(this.reader.peek() instanceof Token.At) {
            this.reader.next();
            var name = this.reader.expect(Token.Identifier.class);
            List<Expression> args = new ArrayList<>();
            if(this.reader.peek() instanceof Token.OpenParen) {
                args = parseTuple(Parser::parseConstant);
            }
            list.add(new Annotation(name.name(), args));
        }
        return list;
    }

    public <T> List<T> parseTuple(Function<Parser, T> mapper) {
        this.reader.expect(Token.OpenParen.class);
        var args = new ArrayList<T>();

        while(!(this.reader.peek() instanceof Token.CloseParen)) {
            args.add(mapper.apply(this));
            if(!(this.reader.peek() instanceof Token.CloseParen)) {
                this.reader.expect(Token.Comma.class);
            }
        }
        this.reader.expect(Token.CloseParen.class);

        return args;
    }

    public Header.Parameter parseParameter() {
        var name = this.reader.expect(Token.Identifier.class);
        var type = this.parseType();
        return new Header.Parameter(name.name(), type);
    }

    public List<Header> parseHeaders() {
        var list = new ArrayList<Header>();

        while(this.reader.hasNext()) {
            var annotations = parseAnnotations();
            var peek = this.reader.peek();
            switch (peek) {
                case Token.FnKeyword _ -> list.add(parseFunction(annotations));
                case Token.TypeKeyword _ -> list.add(parseTypeAlias(annotations));
                default -> throw new RuntimeException("Invalid start of header at " + peek);
            }
        }

        return list;
    }

    public Header.Function parseFunction(List<Annotation> annotations) {
        this.reader.expect(Token.FnKeyword.class);

        var name = this.reader.expect(Token.Identifier.class);
        var params = this.parseTuple(Parser::parseParameter);
        this.reader.expect(Token.RightArrow.class);

        var returnType = this.parseType();

        if(!(this.reader.peek() instanceof Token.OpenBrace)) {
            return new Header.Function(
                    name.name(),
                    returnType,
                    params,
                    null,
                    annotations
            );
        }
        var body = this.parseBody();
        return new Header.Function(
                name.name(),
                returnType,
                params,
                body,
                annotations
        );
    }

    public Header.TypeAlias parseTypeAlias(List<Annotation> annotations) {
        this.reader.expect(Token.TypeKeyword.class);

        var name = this.reader.expect(Token.Identifier.class);
        this.reader.expect(Token.Equals.class);
        var returnType = this.parseType();

        return new Header.TypeAlias(
                name.name(),
                returnType,
                annotations
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
        return switch (this.reader.peek()) {
            case Token.ReturnKeyword _ -> {
                if(this.reader.peek(1) instanceof Token.CloseBrace) {
                    this.reader.next();
                    yield new Statement.Ret(null);
                }
                this.reader.next();
                yield new Statement.Ret(this.parseExpression());
            }
            default -> new Statement.Dropping(this.parseExpression());
        };
    }

    public Expression parseExpression() {
        return parseTerm();
    }

    public Expression parseTerm() {
        var expr = parseInvocation();
        while(reader.peek() instanceof Token.Plus) {
            reader.expect(Token.Plus.class);
            expr = new Expression.Box(
                    new Expression.Addition(
                            new Expression.Unbox(expr),
                            new Expression.Unbox(parseInvocation())
                    )
            );
        }
        return expr;
    }

    public Expression parseInvocation() {
        var expr = parseBoxing();
        while(reader.peek() instanceof Token.OpenParen) {
            expr = new Expression.Invocation(expr, parseTuple(Parser::parseExpression));
        }
        return expr;
    }

    public Expression parseBoxing() {
        if(reader.peek() instanceof Token.BoxKeyword) {
            reader.next();
            return new Expression.Box(parseParens());
        }
        if(reader.peek() instanceof Token.UnboxKeyword) {
            reader.next();
            return new Expression.Unbox(parseParens());
        }
        return parseParens();
    }

    public Expression parseParens() {
        if(reader.peek() instanceof Token.OpenParen) {
            reader.next();
            var expr = parseExpression();
            reader.expect(Token.CloseParen.class);
            return expr;
        }
        return parseConstant();
    }

    public Expression parseConstant() {
        var n = this.reader.next();
        return switch (n) {
            case Token.Integer integer -> new Expression.Box(new Expression.Integer(integer.value()));
            case Token.CString str -> new Expression.CStringValue(
                    str.value().replace("\\0", "\0")
                            .replace("\\n", "\n")
            );
            case Token.String str -> new Expression.StringValue(
                    str.value().replace("\\n", "\n")
            );
            case Token.Identifier id -> new Expression.Variable(id.name());
            default -> throw new RuntimeException("Invalid constant " + n);
        };
    }

    public AstType parseType() {
        var name = this.reader.expect(Token.Identifier.class);
        if(name.name().equals("void")) {
            return new AstType.Void();
        }
        if(name.name().equals("unsafe::raw")) {
            this.reader.expect(Token.OpenParen.class);
            var rt = parseUnboxedType(null);
            this.reader.expect(Token.CloseParen.class);
            return rt;
        } else {
            return new AstType.Boxed(parseUnboxedType(name));
        }
    }

    public AstType parseUnboxedType(Token.Identifier name) {
        if(name == null) {
            name = this.reader.expect(Token.Identifier.class);
        }
        if(name.name().startsWith("i")) {
            var replaced = name.name().replace("i", "");
            try {
                var bits = Integer.parseInt(replaced);
                return new AstType.Integer(bits);
            } catch (Exception ignored) {

            }
        }
        if(name.name().equals("cstr")) {
            return new AstType.CString();
        }
        return new AstType.Unresolved(name.name());
    }
}
