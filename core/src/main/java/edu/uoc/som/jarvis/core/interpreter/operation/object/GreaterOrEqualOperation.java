package edu.uoc.som.jarvis.core.interpreter.operation.object;

import edu.uoc.som.jarvis.core.interpreter.operation.Operation;

import javax.annotation.Nonnull;

/**
 * Computes a <i>greater or equals</i> comparison between the provided {@code source} and {@code args}.
 * <p>
 * This {@link Operation} accepts a single mandatory {@code args} element (the element to compare against the
 * provided {@code source}). Note that both the {@code source} and the {@code args} elements must be {@link Integer}
 * instances.
 */
public class GreaterOrEqualOperation extends ArithmeticOperation {

    /**
     * Computes a <i>greater or equals</i> comparison between the provided {@code source} and {@code target}.
     *
     * @param source the {@link Integer} to invoke the {@link GreaterOrEqualOperation} on
     * @param target the arguments of the operation to invoke
     * @return {@code true} if {@code source} is greater or equal to the given {@code target}, {@code false} otherwise
     */
    @Override
    protected Object doOperation(@Nonnull Integer source, @Nonnull Integer target) {
        return source >= target;
    }
}