package dev.mesbase.infrastructure.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BusinessCacheEvict {
    /**
     * Same permission resource prefix as the read cache.
     */
    String business();

    Scope scope();

    enum Scope {LIST, LIST_AND_DETAIL}
}
