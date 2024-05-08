package com.pocdemo.http.common.lambda;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Lambdas {

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    @FunctionalInterface
    public interface UncheckedFunction<I, O> extends Function<I, O> {
        O tryToApply(I i) throws Exception;

        @Override
        default O apply(I t) {
            try {
                return tryToApply(t);
            } catch (Exception e) {
                sneakyThrow(e);
                return null;
            }
        }
    }

    @FunctionalInterface
    public interface UncheckedBiFunction<P, Q, O> extends BiFunction<P, Q, O> {
        O tryToApply(P p, Q q) throws Exception;

        @Override
        default O apply(P p, Q q) {
            try {
                return tryToApply(p, q);
            } catch (Exception e) {
                sneakyThrow(e);
                return null;
            }
        }
    }

    @FunctionalInterface
    public interface UncheckedSupplier<T> extends Supplier<T> {
        T tryToGet() throws Exception;

        @Override
        default T get() {
            try {
                return tryToGet();
            } catch (Exception e) {
                sneakyThrow(e);
                return null;
            }
        }
    }

    @FunctionalInterface
    public interface UncheckedConsumer<T> extends Consumer<T> {
        void tryToAccept(T t) throws Exception;

        @Override
        default void accept(T t) {
            try {
                tryToAccept(t);
            } catch (Exception e) {
                sneakyThrow(e);
            }
        }
    }

    @FunctionalInterface
    public interface UncheckedBiConsumer<P, Q> extends BiConsumer<P, Q> {
        void tryToAccept(P p, Q q) throws Exception;

        @Override
        default void accept(P out, Q q) {
            try {
                tryToAccept(out, q);
            } catch (Exception e) {
                sneakyThrow(e);
            }
        }
    }

    @FunctionalInterface
    public interface UncheckedRunnable extends Runnable {
        void tryToRun() throws Exception;

        @Override
        default void run() {
            try {
                tryToRun();
            } catch (Exception e) {
                sneakyThrow(e);
            }
        }
    }

    public static <T extends Throwable> void tryAndThrow(UncheckedRunnable runnable, Function<String, T> excp)
            throws T {
        try {
            runnable.run();
        } catch (Exception e) {
            throw excp.apply(e.getMessage());
        }
    }

    public static void run(UncheckedRunnable runnable) {
        runnable.run();
    }

    public static <T> T get(UncheckedSupplier<T> supplier) {
        return supplier.get();
    }

    public static <T> void accept(T t, UncheckedConsumer<T> consumer) {
        consumer.accept(t);
    }

    public static <I, O> O apply(I i, UncheckedFunction<I, O> function) {
        return function.apply(i);
    }

    public static <T> void chain(UncheckedSupplier<T> supplier, UncheckedConsumer<T> consumer) {
        consumer.accept(supplier.get());
    }

    public static <T> void ifPresent(UncheckedSupplier<T> supplier, UncheckedConsumer<T> consumer) {
        T obj = supplier.get();
        if (null != obj) {
            consumer.accept(obj);
        }
    }

    public static <T, R> R ifNotNullElseGet(Supplier<T> supplier, Function<T, R> func, Supplier<R> supplier2) {
        T t = supplier.get();
        if (null != t) {
            return func.apply(t);
        }
        return supplier2.get();
    }

    public static <T extends AutoCloseable, R> R closing(UncheckedSupplier<T> supplier,
            UncheckedFunction<T, R> function) {
        try (T t = supplier.get()) {
            return function.apply(t);
        } catch (Exception e) {
            sneakyThrow(e);
            return null;
        }
    }

    public static <T extends AutoCloseable> void takeAndClose(UncheckedSupplier<T> supplier,
            UncheckedConsumer<T> consumer) {
        try (T t = supplier.get()) {
            consumer.accept(t);
        } catch (Exception e) {
            sneakyThrow(e);
        }
    }
}
