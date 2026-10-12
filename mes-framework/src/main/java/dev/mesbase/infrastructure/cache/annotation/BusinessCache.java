package dev.mesbase.infrastructure.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BusinessCache {
    /**
     * Permission resource prefix without action, e.g. business:pro:workorder.
     */
    String business();

    Type type();

    /**
     * Explicit query or ID argument index; never inferred from parameter names.
     */
    int argument() default 0;

    enum Type {LIST, DETAIL}
}
