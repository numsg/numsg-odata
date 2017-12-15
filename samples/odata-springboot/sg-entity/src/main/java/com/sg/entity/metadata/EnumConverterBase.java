package com.sg.entity.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Created by xiaodiming on 2016/6/12.
 * 实体内枚举转换基类
 */
public abstract class EnumConverterBase<E extends EnumValueContract> {

    /**
     * 日期记录器
     */
    protected Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * 枚举对象转换为Integer
     *
     * @param value 枚举对象
     * @return 枚举Integer值
     */
    protected Integer toDatabaseValue(E value) {
        if (value == null)
            return null;
        return value.getParamType();
    }

    /**
     * Integer转换为枚举对象
     *
     * @param value 枚举Integer值
     * @return
     */
    protected E toEntityAttribute(Integer value) {
        if(value==null)
            return null;
        Class<E> enumClass = getGenericClass();
        E[] objs = enumClass.getEnumConstants();
        Method paramInt = null;
        try {
            paramInt = enumClass.getMethod("getParamType");
            for (E obj : objs) {
                Integer typeInt = (Integer) paramInt.invoke(obj);
                if (typeInt.equals(value)) {
                    return obj;
                }
            }
        } catch (Exception e) {
            log.error(enumClass.getName() + "_" + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取子类的泛型类型
     *
     * @return
     */
    private Class<E> getGenericClass() {
        Type genType = getClass().getGenericSuperclass();
        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
        return (Class) params[0];
    }

}
