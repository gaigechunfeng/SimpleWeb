package com.wk.web.annotations;

import java.lang.annotation.*;

/**
 * 响应的数据以json格式返回，作用于controller
 * Created by 005689 on 2016/6/15.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Json {
}
