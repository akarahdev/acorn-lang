package acorn.token;

public interface Token {
    SpanData span();

    record Identifier(java.lang.String name, SpanData span) implements Token {}

    record Integer(long value, SpanData span) implements Token {}

    record Floating(double value, SpanData span) implements Token {}

    record CString(java.lang.String value, SpanData span) implements Token {}

    record String(java.lang.String value, SpanData span) implements Token {}

    record NamespaceKeyword(SpanData span) implements Token {}

    record FnKeyword(SpanData span) implements Token {}

    record ArrayKeyword(SpanData span) implements Token {}

    record TypeKeyword(SpanData span) implements Token {}

    record ReturnKeyword(SpanData span) implements Token {}

    record StructKeyword(SpanData span) implements Token {}

    record BoxKeyword(SpanData span) implements Token {}

    record UnboxKeyword(SpanData span) implements Token {}

    record RightArrow(SpanData span) implements Token {}

    record Equals(SpanData span) implements Token {}

    record At(SpanData span) implements Token {}

    record Period(SpanData span) implements Token {}

    record Comma(SpanData span) implements Token {}

    record OpenBrace(SpanData span) implements Token {}

    record CloseBrace(SpanData span) implements Token {}

    record OpenBracket(SpanData span) implements Token {}

    record CloseBracket(SpanData span) implements Token {}

    record OpenParen(SpanData span) implements Token {}

    record CloseParen(SpanData span) implements Token {}

    record Plus(SpanData span) implements Token {}

    record Minus(SpanData span) implements Token {}

    record Star(SpanData span) implements Token {}

    record Slash(SpanData span) implements Token {}

    record Colon(SpanData span) implements Token {}
}
