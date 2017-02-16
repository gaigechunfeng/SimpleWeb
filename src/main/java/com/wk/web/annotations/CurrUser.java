package com.wk.web.annotations;

import java.lang.annotation.*;

/**
 * Created by 005689 on 2016/11/29.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface CurrUser {
}
