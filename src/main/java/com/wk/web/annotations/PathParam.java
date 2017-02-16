package com.wk.web.annotations;

import java.lang.annotation.*;

/**
 * Created by 005689 on 2016/9/23.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface PathParam {

    String value();
}
