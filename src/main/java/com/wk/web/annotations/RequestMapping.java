package com.wk.web.annotations;

import java.lang.annotation.*;

/**
 * 匹配请求，作用于controller
 * Created by 005689 on 2016/6/13.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequestMapping {
    String value();
}
