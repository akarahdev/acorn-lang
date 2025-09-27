package acorn.parser;

import acorn.parser.ast.*;
import acorn.reader.Reader;
import acorn.token.SpannedException;
import acorn.token.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class Parser {

    Reader<List<Token>, Token> reader;

    public static Parser create(List<Token> tokens) {
        var p = new Parser();
        p.reader = Reader.create(tokens, List::get, List::size, (a, b) -> {
            throw new SpannedException(
                a.span(),
                new SpannedException.ErrorType.UnexpectedToken(
                    List.of(b),
                    a.getClass()
                )
            );
        });
        return p;
    }

    public List<Annotation> parseAnnotations() {
        var list = new ArrayList<Annotation>();

        while (this.reader.peek() instanceof Token.At) {
            this.reader.next();
            var name = this.reader.expect(Token.Identifier.class);
            List<Expression> args = new ArrayList<>();
            if (this.reader.peek() instanceof Token.OpenParen) {
                args = parseTuple(Parser::parseConstant);
            }
            list.add(new Annotation(name.name(), args));
        }
        return list;
    }

    public <T> List<T> parseTuple(Function<Parser, T> mapper) {
        this.reader.expect(Token.OpenParen.class);
        var args = new ArrayList<T>();

        while (!(this.reader.peek() instanceof Token.CloseParen)) {
            args.add(mapper.apply(this));
            if (!(this.reader.peek() instanceof Token.CloseParen)) {
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

        while (this.reader.hasNext()) {
            var annotations = parseAnnotations();
            var peek = this.reader.peek();
            switch (peek) {
                case Token.FnKeyword _ -> list.add(parseFunction(annotations));
                case Token.TypeKeyword _ -> list.add(
                    parseTypeAlias(annotations)
                );
                default -> throw new SpannedException(
                    peek.span(),
                    new SpannedException.ErrorType.UnexpectedToken(
                        List.of(Token.FnKeyword.class, Token.TypeKeyword.class),
                        peek.getClass()
                    )
                );
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

        if (!(this.reader.peek() instanceof Token.OpenBrace)) {
            return new Header.Function(
                name.name(),
                returnType,
                params,
                null,
                annotations,
                name.span()
            );
        }
        var body = this.parseBody();
        return new Header.Function(
            name.name(),
            returnType,
            params,
            body,
            annotations,
            name.span()
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
            annotations,
            name.span()
        );
    }

    public List<Statement> parseBody() {
        this.reader.expect(Token.OpenBrace.class);
        var stmts = new ArrayList<Statement>();
        while (!(this.reader.peek() instanceof Token.CloseBrace)) {
            stmts.add(this.parseStorage());
        }
        this.reader.expect(Token.CloseBrace.class);
        return stmts;
    }

    public Statement parseStorage() {
        var base = this.parseStatement();
        if (
            base instanceof Statement.Dropping(Expression expr) &&
            this.reader.peek() instanceof Token.Equals
        ) {
            this.reader.next();
            var resolve = this.parseExpression();
            return new Statement.StoreValue(expr, resolve);
        }
        return base;
    }

    public Statement parseStatement() {
        return switch (this.reader.peek()) {
            case Token.ReturnKeyword _ -> {
                if (this.reader.peek(1) instanceof Token.CloseBrace) {
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
        while (reader.peek() instanceof Token.Plus) {
            var sym = reader.expect(Token.Plus.class);
            expr = new Expression.Box(
                new Expression.Addition(
                    new Expression.Unbox(expr),
                    new Expression.Unbox(parseInvocation()),
                    sym.span()
                )
            );
        }
        return expr;
    }

    public Expression parseInvocation() {
        var expr = parseBoxing();
        while (reader.peek() instanceof Token.OpenParen paren) {
            expr = new Expression.Invocation(
                expr,
                parseTuple(Parser::parseExpression),
                paren.span()
            );
        }
        return expr;
    }

    public Expression parseBoxing() {
        if (reader.peek() instanceof Token.BoxKeyword) {
            reader.next();
            return new Expression.Box(parseParens());
        }
        if (reader.peek() instanceof Token.UnboxKeyword) {
            reader.next();
            return new Expression.Unbox(parseParens());
        }
        return parseParens();
    }

    public Expression parseParens() {
        if (reader.peek() instanceof Token.OpenParen) {
            reader.next();
            var expr = parseExpression();
            reader.expect(Token.CloseParen.class);
            return expr;
        }
        return parseFieldAccess();
    }

    public Expression parseFieldAccess() {
        var expr = parseConstant();
        while (true) {
            if (this.reader.peek() instanceof Token.Period period) {
                this.reader.next();
                var field = this.reader.expect(Token.Identifier.class).name();
                expr = new Expression.FieldAccess(expr, field, period.span());
            } else if (
                this.reader.peek() instanceof Token.OpenBracket bracket
            ) {
                this.reader.next();
                expr = new Expression.Subscript(
                    expr,
                    new Expression.Unbox(parseExpression()),
                    bracket.span()
                );
                this.reader.expect(Token.CloseBracket.class);
            } else {
                break;
            }
        }
        return expr;
    }

    public Expression parseConstant() {
        var n = this.reader.next();
        return switch (n) {
            case Token.Integer integer -> new Expression.Box(
                new Expression.Integer(integer.value(), integer.span())
            );
            case Token.CString str -> new Expression.CStringValue(
                str.value().replace("\\0", "\0").replace("\\n", "\n"),
                str.span()
            );
            case Token.String str -> new Expression.StringValue(
                str.value().replace("\\n", "\n"),
                str.span()
            );
            case Token.Identifier id -> new Expression.Variable(
                id.name(),
                id.span()
            );
            case Token.OpenBrace _ -> parseStructLiteral(this::parseExpression);
            case Token.OpenBracket _ -> parseArrayLiteral(
                this::parseExpression
            );
            default -> throw new SpannedException(
                n.span(),
                new SpannedException.ErrorType.UnexpectedToken(
                    List.of(
                        Token.Integer.class,
                        Token.CString.class,
                        Token.String.class,
                        Token.Identifier.class,
                        Token.OpenBrace.class,
                        Token.OpenBracket.class
                    ),
                    n.getClass()
                )
            );
        };
    }

    public Expression parseStructLiteral(Supplier<Expression> exprObtainer) {
        var params = new ArrayList<Expression.StructLiteral.Field>();

        while (!(this.reader.peek() instanceof Token.CloseBrace)) {
            var name = this.reader.expect(Token.Identifier.class);
            var type = this.parseType();
            this.reader.expect(Token.Equals.class);
            var value = exprObtainer.get();
            params.add(
                new Expression.StructLiteral.Field(name.name(), type, value)
            );
            if (!(this.reader.peek() instanceof Token.CloseBrace)) {
                this.reader.expect(Token.Comma.class);
            }
        }
        var cb = this.reader.expect(Token.CloseBrace.class);

        return new Expression.Box(
            new Expression.StructLiteral(params, cb.span())
        );
    }

    public Expression parseArrayLiteral(Supplier<Expression> exprObtainer) {
        var params = new ArrayList<Expression>();

        while (!(this.reader.peek() instanceof Token.CloseBracket)) {
            var value = exprObtainer.get();
            params.add(value);
            if (!(this.reader.peek() instanceof Token.CloseBracket)) {
                this.reader.expect(Token.Comma.class);
            }
        }
        var cb = this.reader.expect(Token.CloseBracket.class);

        return new Expression.Box(
            new Expression.ArrayLiteral(params, cb.span())
        );
    }

    public AstType parseType() {
        if (this.reader.peek() instanceof Token.OpenBracket ob) {
            this.reader.next();
            var innerType = this.parseType();
            this.reader.expect(Token.CloseBracket.class);
            return new AstType.Boxed(new AstType.Array(innerType, ob.span()));
        }
        if (this.reader.peek() instanceof Token.OpenBrace ob) {
            var params = new ArrayList<Header.Parameter>();
            this.reader.expect(Token.OpenBrace.class);
            while (!(this.reader.peek() instanceof Token.CloseBrace)) {
                params.add(parseParameter());
                if (!(this.reader.peek() instanceof Token.CloseBrace)) {
                    this.reader.expect(Token.Comma.class);
                }
            }
            this.reader.expect(Token.CloseBrace.class);

            return new AstType.Boxed(new AstType.Struct(params, ob.span()));
        }
        if (this.reader.peek() instanceof Token.UnboxKeyword) {
            this.reader.next();
            return parseUnboxedType(null);
        }
        var name = this.reader.expect(Token.Identifier.class);
        if (name.name().equals("void")) {
            return new AstType.Void(name.span());
        }
        return new AstType.Boxed(parseUnboxedType(name));
    }

    public AstType parseUnboxedType(Token.Identifier name) {
        if (name == null) {
            name = this.reader.expect(Token.Identifier.class);
        }
        if (name.name().startsWith("i")) {
            var replaced = name.name().replace("i", "");
            try {
                var bits = Integer.parseInt(replaced);
                return new AstType.Integer(bits, name.span());
            } catch (Exception ignored) {}
        }
        if (name.name().equals("libc::ptr")) {
            return new AstType.LibCPointer(name.span());
        }
        return new AstType.Unresolved(name.name(), name.span());
    }
}
