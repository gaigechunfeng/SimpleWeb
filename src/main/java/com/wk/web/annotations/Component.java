package com.wk.web.annotations;

import java.lang.annotation.*;

/**
 * Created by 005689 on 2016/7/3.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE,ElementType.METHOD})
public @interface Component {
    String value() default  "";
}
