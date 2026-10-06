package dev.mesbase.common.openapi;

import java.lang.annotation.*;

/** 必须显式声明稳定API标识；漏标由OpenAPI拦截器拒绝。 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OpenApiEndpoint {
    /** 方法必须声明合法API标识；类级注解仅提供分组描述。 */
    String value() default "";
    String description() default "";
}
