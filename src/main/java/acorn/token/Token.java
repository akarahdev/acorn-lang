package acorn.token;

public interface Token {
    record Identifier(java.lang.String name) implements Token {

    }

    record Integer(long value) implements Token {

    }

    record Floating(double value) implements Token {

    }

    record CString(java.lang.String value) implements Token {

    }

    record String(java.lang.String value) implements Token {

    }

    record FnKeyword() implements Token {

    }

    record ReturnKeyword() implements Token {

    }

    record RightArrow() implements Token {

    }

    record At() implements Token {

    }

    record Comma() implements Token {

    }

    record OpenBrace() implements Token {

    }

    record CloseBrace() implements Token {

    }

    record OpenParen() implements Token {

    }

    record CloseParen() implements Token {

    }

    record Plus() implements Token {

    }

    record Minus() implements Token {

    }

    record Star() implements Token {

    }

    record Slash() implements Token {

    }
}
