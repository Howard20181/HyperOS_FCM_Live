package io.github.howard20181.hyperos.fcmlive;

import java.util.function.Consumer;

public class Utils {
    public static <T> T evaluate(T target, Consumer<T> action) {
        action.accept(target);
        return target;
    }
}
