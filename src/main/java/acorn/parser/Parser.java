package acorn.parser;

import acorn.parser.ast.*;
import acorn.reader.Reader;
import acorn.token.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

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
            stmts.add(this.parseStorage());
        }
        this.reader.expect(Token.CloseBrace.class);
        return stmts;
    }

    public Statement parseStorage() {
        var base = this.parseStatement();
        if(base instanceof Statement.Dropping(Expression expr) && this.reader.peek() instanceof Token.Equals) {
            this.reader.next();
            var resolve = this.parseExpression();
            return new Statement.StoreValue(expr, resolve);
        }
        return base;
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
        return parseFieldAccess();
    }

    public Expression parseFieldAccess() {
        var expr = parseSubscript();
        if(this.reader.peek() instanceof Token.Period) {
            this.reader.next();
            var field = this.reader.expect(Token.Identifier.class).name();
            expr = new Expression.FieldAccess(expr, field);
        }
        return expr;
    }

    public Expression parseSubscript() {
        var expr = parseConstant();
        if(this.reader.peek() instanceof Token.OpenBracket) {
            this.reader.next();
            expr = new Expression.Subscript(expr, new Expression.Unbox(parseExpression()));
            this.reader.expect(Token.CloseBracket.class);
        }
        return expr;
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
            case Token.OpenBrace _ -> parseStructLiteral(this::parseExpression);
            case Token.OpenBracket _ -> parseArrayLiteral(this::parseExpression);
            default -> throw new RuntimeException("Invalid constant " + n);
        };
    }

    public Expression parseStructLiteral(Supplier<Expression> exprObtainer) {
        var params = new ArrayList<Expression.StructLiteral.Field>();

        while(!(this.reader.peek() instanceof Token.CloseBrace)) {
            var name = this.reader.expect(Token.Identifier.class);
            var type = this.parseType();
            this.reader.expect(Token.Equals.class);
            var value = exprObtainer.get();
            params.add(new Expression.StructLiteral.Field(name.name(), type, value));
            if(!(this.reader.peek() instanceof Token.CloseBrace)) {
                this.reader.expect(Token.Comma.class);
            }
        }
        this.reader.expect(Token.CloseBrace.class);

        return new Expression.Box(new Expression.StructLiteral(params));
    }

    public Expression parseArrayLiteral(Supplier<Expression> exprObtainer) {
        var params = new ArrayList<Expression>();

        while(!(this.reader.peek() instanceof Token.CloseBracket)) {
            var value = exprObtainer.get();
            params.add(value);
            if(!(this.reader.peek() instanceof Token.CloseBracket)) {
                this.reader.expect(Token.Comma.class);
            }
        }
        this.reader.expect(Token.CloseBracket.class);

        return new Expression.Box(new Expression.ArrayLiteral(params));
    }

    public AstType parseType() {
        if(this.reader.peek() instanceof Token.OpenBracket) {
            this.reader.next();
            var innerType = this.parseType();
            this.reader.expect(Token.CloseBracket.class);
            return new AstType.Boxed(new AstType.Array(innerType));
        }
        if(this.reader.peek() instanceof Token.OpenBrace) {
            var params = new ArrayList<Header.Parameter>();
            this.reader.expect(Token.OpenBrace.class);
            while(!(this.reader.peek() instanceof Token.CloseBrace)) {
                params.add(parseParameter());
                if(!(this.reader.peek() instanceof Token.CloseBrace)) {
                    this.reader.expect(Token.Comma.class);
                }
            }
            this.reader.expect(Token.CloseBrace.class);

            return new AstType.Boxed(new AstType.Struct(params));
        }
        if(this.reader.peek() instanceof Token.UnboxKeyword) {
            this.reader.next();
            return parseUnboxedType(null);
        }
        var name = this.reader.expect(Token.Identifier.class);
        if(name.name().equals("void")) {
            return new AstType.Void();
        }
        return new AstType.Boxed(parseUnboxedType(name));
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
        if(name.name().equals("libc::ptr")) {
            return new AstType.LibCPointer();
        }
        return new AstType.Unresolved(name.name());
    }
}
