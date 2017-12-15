package com.sg.odata.service.util;

import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.ex.ODataException;
import org.hibernate.jpa.internal.metamodel.AbstractType;

import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

/**
 * Created by gaoqiang on 2017/4/26.
 */
public class NumsgEntityTypeUtil {
    /*
     * 获取主键
      */
    public static String getId(EntityType entityType) throws ODataException {
        try
        {
            return entityType.getId(entityType.getIdType().getJavaType()).getName();
        }catch (Exception ex){
            throw new ODataException("Numsg: not find Id!"); //如果没有主键，默认设置ID为主键
        }
    }

    /*
    * 根据entityFullTypeName 获取EntityType
    * */
    public static EntityType getEntityTypeByEntityFullTypeName(Metamodel metamodel, String entityFullTypeName) throws ODataException {
        for (EntityType entityType : metamodel.getEntities()){
            if(((AbstractType)entityType).getTypeName().equals(entityFullTypeName)) {
                return entityType;
            }
        }
        throw new ODataException("Numsg: not find EntityType!");
    }

    /*
    * 获取EdmType
    * */
    public static EdmPrimitiveTypeKind getEdmType(final Class<?> type) {
        if (type == String.class) {
            return EdmPrimitiveTypeKind.String;
        } else if (type == boolean.class || type == Boolean.class) {
            return EdmPrimitiveTypeKind.Boolean;
        } else if (type == byte.class || type == Byte.class) {
            return EdmPrimitiveTypeKind.SByte;
        } else if (type == short.class || type == Short.class) {
            return EdmPrimitiveTypeKind.Int16;
        } else if (type == int.class || type == Integer.class) {
            return EdmPrimitiveTypeKind.Int32;
        } else if (type == long.class || type == Long.class) {
            return EdmPrimitiveTypeKind.Int64;
        } else if (type == double.class || type == Double.class) {
            return EdmPrimitiveTypeKind.Double;
        } else if (type == float.class || type == Float.class) {
            return EdmPrimitiveTypeKind.Single;
        } else if (type == BigInteger.class || type == BigDecimal.class) {
            return EdmPrimitiveTypeKind.Decimal;
        } else if (type == Byte[].class || type == byte[].class) {
            return EdmPrimitiveTypeKind.Binary;
        } else if (type == Date.class) {
            return EdmPrimitiveTypeKind.Date;
        } else if (type == Calendar.class) {
            return EdmPrimitiveTypeKind.DateTimeOffset;
        } else if (type == UUID.class) {
            return EdmPrimitiveTypeKind.Guid;
        } else {
            return null;
//            throw new UnsupportedOperationException("Not yet supported type '" + type + "'.");
        }
    }
}
