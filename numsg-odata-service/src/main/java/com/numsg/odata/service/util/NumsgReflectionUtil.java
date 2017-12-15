package com.numsg.odata.service.util;

import com.numsg.odata.service.annotation.ODataColumn;
import com.numsg.odata.service.annotation.ODataEntity;
import com.numsg.odata.service.enumx.EntityRelationType;
import org.apache.olingo.commons.api.ex.ODataException;
import org.hibernate.jpa.internal.metamodel.AbstractType;

import javax.persistence.*;
import javax.persistence.metamodel.EntityType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Created by gaoqiang on 2017/4/26.
 */
public class NumsgReflectionUtil {

    /*
    * 创建一个实例
    * */
    public static <T> Class<T> newClass(String className) throws ODataException {
        try {
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new ODataException("Cannot create class of: " + className, e);
        }
    }

    /*
    * 判断是否是数据库表列
    * */
    public static boolean isHibernateCloumn(Field field){
        Annotation[] annotations = field.getDeclaredAnnotations();
        if(annotations==null){
            return false;
        }
        for(Annotation annotation : annotations){
            if(annotation instanceof Column) {
                return true;
            }
        }
        return  false;
    }

    /*
    * 判断是否是数据库表列
    * */
    public static boolean isHibernateId(Field field){
        Annotation[] annotations = field.getDeclaredAnnotations();
        if(annotations==null){
            return false;
        }
        for(Annotation annotation : annotations){
            if(annotation instanceof Id) {
                return true;
            }
        }
        return  false;
    }
    /*
    * 判断是否是数据库表列
    * */
    public static EntityRelationType getHibernateRelationType(Field field){
        Annotation[] annotations = field.getDeclaredAnnotations();
        if(annotations==null){
            return EntityRelationType.None;
        }
        for(Annotation annotation : annotations){
            if(annotation instanceof OneToMany) {
                return EntityRelationType.OneToMany;
            }else  if(annotation instanceof ManyToMany){
                return EntityRelationType.ManyToMany;
            }
            else  if(annotation instanceof OneToOne){
                return EntityRelationType.OneToOne;
            }
            else  if(annotation instanceof ManyToOne){
                return EntityRelationType.ManyToOne;
            }
        }
        return  EntityRelationType.None;
    }

    public static boolean isODataEntity(EntityType entityType) throws ODataException{
        Class clazz = NumsgReflectionUtil.newClass(((AbstractType)entityType).getTypeName());
        for(Annotation annotation: clazz.getAnnotations() ) {
            if(annotation instanceof ODataEntity && !((ODataEntity) annotation).expose()){
                return  true;
            }
        }
        return  false;
    }

    public static boolean isODataColumn(Field field) throws ODataException{
        Annotation[] annotations = field.getDeclaredAnnotations();
        for(Annotation annotation : annotations){
            if(annotation instanceof ODataColumn && !((ODataColumn) annotation).expose()) {
                return true;
            }
        }
        return  false;
    }
}
