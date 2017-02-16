package com.wk.web.annotations;

import java.lang.annotation.*;

/**
 * 不需要进行Csrf攻击检测
 * Created by 005689 on 2016/10/12.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface NoCsrf {
}
