package com.hmdp.annotation;

import com.hmdp.enums.RateLimitDimension;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String key() default "";
    int count();
    int windowSeconds();
    RateLimitDimension[] dimensions() default {RateLimitDimension.METHOD};
    String message() default "请求过于频繁，请稍后再试";
}
