package dev.hata.scheduler.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative rate-limit rules evaluated by {@link RateLimitAspect} before a
 * protected endpoint executes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    Rule[] value();

    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Rule {
        String policy();

        String keyExpression() default "";

        String message() default "Rate limit exceeded. Wait before trying again.";
    }
}
