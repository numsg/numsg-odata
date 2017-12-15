package com.sg.odata.service.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by gaoqiang on 2017/12/11.
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface ODataColumn {
    //是否暴露该成员
    boolean expose() default true;
}
