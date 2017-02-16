package com.wk.web.annotations;


import com.wk.web.utils.Permissions;

import java.lang.annotation.*;

/**
 * Created by 005689 on 2016/11/29.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Permission {
    int value() default Permissions.ADMIN;
}
