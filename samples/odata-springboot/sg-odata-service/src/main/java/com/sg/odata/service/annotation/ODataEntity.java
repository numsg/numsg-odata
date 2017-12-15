package com.sg.odata.service.annotation;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Created by gaoqiang on 2017/12/11.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Order(Ordered.HIGHEST_PRECEDENCE)
public @interface ODataEntity {
    //是否暴露该成员
    boolean expose() default true;
}
