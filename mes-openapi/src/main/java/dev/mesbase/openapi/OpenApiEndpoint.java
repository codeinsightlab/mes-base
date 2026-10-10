package dev.mesbase.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SOURCE_PRODUCT_PORT: stable API metadata; never grants access to a handler.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OpenApiEndpoint {
    String value() default "";

    String description() default "";
}
