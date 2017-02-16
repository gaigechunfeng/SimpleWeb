package com.wk.web.annotations;

import java.lang.annotation.*;

/**
 * Created by 005689 on 2016/6/13.
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Controller {
    String name() default "";
}
