package acorn.reader;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class Reader<T, E> {

    T value;
    int index = 0;
    BiFunction<T, Integer, E> indexFunction;
    Function<T, Integer> lengthFunction;

    public static <T, E> Reader<T, E> create(
        T value,
        BiFunction<T, Integer, E> indexFunction,
        Function<T, Integer> lengthFunction
    ) {
        var r = new Reader<T, E>();
        r.value = value;
        r.indexFunction = indexFunction;
        r.lengthFunction = lengthFunction;
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

    public E next() {
        try {
            return indexFunction.apply(value, index++);
        } catch (Exception e) {
            return null;
        }
    }

    public E peek() {
        try {
            return indexFunction.apply(value, index);
        } catch (Exception e) {
            return null;
        }
    }

    public E peek(int ahead) {
        try {
            return indexFunction.apply(value, index + ahead);
        } catch (Exception e) {
            return null;
        }
    }

    public E expect(Predicate<E> predicate) {
        assert predicate.test(peek());
        return next();
    }

    public <EA extends E> EA expect(Class<EA> clazz) {
        assert clazz.isInstance(peek());
        return clazz.cast(next());
    }
}
