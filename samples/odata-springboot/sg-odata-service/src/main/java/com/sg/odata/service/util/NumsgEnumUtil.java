package com.sg.odata.service.util;

import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by shenhao on 2017/7/27.
 */
public class NumsgEnumUtil {
    private static List<Class> cachedType = new ArrayList<>();
    private static Map<Object,String> cachedEnum = new HashMap<>();

    public static void cacheEnum(Class type){
        if(!cachedType.contains(type)){
            Object[] list = type.getEnumConstants();
            for(Object i : list){
                cachedEnum.put(i,i.toString());
            }
            cachedType.add(type);
        }
    }

    public static Object getEnum(String enumName) throws ODataApplicationException {
        Object result = null;
        if(cachedEnum.values().contains(enumName)){
            List<Map.Entry<Object,String>> list =cachedEnum.entrySet().stream().filter(n->n.getValue().equals(enumName)).collect(Collectors.toList());
            if(list.size() > 1){
                throw new ODataApplicationException("Repeated Enum Name",
                        HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
            }
            result = list.get(0).getKey();
        }
        return result;
    }
}
