package com.wk.web.annotations;

import java.lang.annotation.*;

/**
 * Created by 005689 on 2016/6/14.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Bean {
    String name() default "";
}
