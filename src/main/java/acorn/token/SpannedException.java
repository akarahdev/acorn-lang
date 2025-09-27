package acorn.token;

import acorn.parser.ast.AstType;
import java.util.List;
import java.util.stream.Collectors;

public class SpannedException extends RuntimeException {

    private final SpanData span;
    private final ErrorType error;

    public SpannedException(SpanData span, ErrorType error) {
        this.span = span;
        this.error = error;
    }

    public SpanData getSpan() {
        return span;
    }

    public ErrorType error() {
        return this.error;
    }

    public sealed interface ErrorType {
        public String message();

        public record UnexpectedToken(
            List<? extends Class<? extends Token>> expected,
            Class<? extends Token> found
        ) implements ErrorType {
            @Override
            public String message() {
                return (
                    "Expected tokens " +
                    expected
                        .stream()
                        .map(Class::getSimpleName)
                        .collect(Collectors.joining(", ")) +
                    ", found token `" +
                    found.getSimpleName() +
                    "`."
                );
            }
        }

        record DoesNotSupportSubscripting(AstType type) implements ErrorType {
            @Override
            public String message() {
                return "Type `" + type + "` does not support subscripting.";
            }
        }

        record DoesNotSupportPathing(AstType type) implements ErrorType {
            @Override
            public String message() {
                return "Type `" + type + "` can not be pathed into.";
            }
        }

        record DoesNotSupportField(AstType type, String fieldName) implements
            ErrorType {
            @Override
            public String message() {
                return (
                    "Field `" +
                    fieldName +
                    "` was not found on type `" +
                    type +
                    "`."
                );
            }
        }

        record VariableDoesNotExist(String fieldName) implements ErrorType {
            @Override
            public String message() {
                return "Variable `" + fieldName + "` does not exist.";
            }
        }

        record NotValidPath() implements ErrorType {
            @Override
            public String message() {
                return "Paths must be comprised of valid identifiers.";
            }
        }

        record WrongType(List<AstType> expected, AstType found) implements
            ErrorType {
            @Override
            public String message() {
                return (
                    "Expected types `" +
                    expected
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", ")) +
                    "`, found type `" +
                    found +
                    "`."
                );
            }
        }
    }
}
