package acorn.reader;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class Reader<T, E> {

    T value;
    int index = 0;
    BiFunction<T, Integer, E> indexFunction;
    Function<T, Integer> lengthFunction;
    BiConsumer<E, Class<? extends E>> predicateFailureFunction;
    Runnable peekFailureFunction;

    public static <T, E> Reader<T, E> create(
        T value,
        BiFunction<T, Integer, E> indexFunction,
        Function<T, Integer> lengthFunction,
        BiConsumer<E, Class<? extends E>> exceptionFunction,
        Runnable peekFailureFunction
    ) {
        var r = new Reader<T, E>();
        r.value = value;
        r.indexFunction = indexFunction;
        r.lengthFunction = lengthFunction;
        r.predicateFailureFunction = exceptionFunction;
        r.peekFailureFunction = peekFailureFunction;
        return r;
    }

    public int index() {
        return this.index;
    }

    public int length() {
        return this.lengthFunction.apply(value);
    }

    public boolean hasNext() {
        return index < length();
    }

    public T value() {
        return this.value;
    }

    public E next() {
        try {
            return indexFunction.apply(value, index++);
        } catch (Exception e) {
            peekFailureFunction.run();
            return null;
        }
    }

    public E peek() {
        try {
            return indexFunction.apply(value, index);
        } catch (Exception e) {
            peekFailureFunction.run();
            return null;
        }
    }

    public E peek(int ahead) {
        try {
            return indexFunction.apply(value, index + ahead);
        } catch (Exception e) {
            peekFailureFunction.run();
            return null;
        }
    }

    public E expect(Predicate<E> predicate) {
        if (!predicate.test(peek())) {
            this.predicateFailureFunction.accept(peek(), null);
        }
        return next();
    }

    public <EA extends E> EA expect(Class<EA> clazz) {
        if (!clazz.isInstance(peek())) {
            this.predicateFailureFunction.accept(peek(), clazz);
        }
        return clazz.cast(next());
    }
}
