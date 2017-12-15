package com.sg.odata.service.datasource;


import com.sg.odata.service.util.NumsgEntityUtil;
import com.sg.odata.service.visitor.NumsgFilterExpressionVisitor;
import com.sg.odata.service.util.NumsgReflectionUtil;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;
import org.hibernate.collection.internal.PersistentBag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by gaoqiang on 2017/4/26.
 */

@Component
public class NumsgDataProvider {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    public EntityCollection read(final UriInfo uriInfo, EdmEntitySet edmEntitySet, String naviFetch)
            throws ODataApplicationException {
        try {
            List<Object> listEntity = readDatabase(uriInfo, edmEntitySet, naviFetch);
            return ConvertEntityToOdataEntitySet(uriInfo, listEntity, edmEntitySet);
        } catch (Exception ex) {
            throw new ODataApplicationException(ex.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
        }
    }

    public EntityCollection readX(final UriInfo uriInfo, EdmEntitySet edmEntitySet, String naviFetch)
            throws ODataApplicationException {
        try {
            List<Object> listEntity = readDatabase(uriInfo, edmEntitySet, naviFetch);
            return ConvertEntityToOdataEntitySetX(uriInfo, listEntity, edmEntitySet);
        } catch (Exception ex) {
            throw new ODataApplicationException(ex.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
        }
    }

    public int readEntityCount(final UriInfo uriInfo, EdmEntitySet entityMain, EdmEntitySet entityNavigate) throws ODataApplicationException {
        try {
            return readDataCount(uriInfo, entityMain, entityNavigate);
        } catch (ODataException ex) {
            throw new ODataApplicationException("", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
        }
    }

    public Object readCount(final UriInfo uriInfo, EdmEntitySet edmEntitySet) throws ODataApplicationException {
        try {
            List<Object> listEntity = readDatabase(uriInfo, edmEntitySet, null);
            return listEntity.get(0);
        } catch (ODataException ex) {
            throw new ODataApplicationException("", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
        }
    }

    private int readDataCount(final UriInfo uriInfo, EdmEntitySet edmEntitySet, EdmEntitySet entityNavigate) throws ODataException {
        EntityManager em = entityManagerFactory.createEntityManager();
        String entityName = edmEntitySet.getEntityType().getFullQualifiedName().getFullQualifiedNameAsString();
        Class cls = NumsgReflectionUtil.newClass(entityName);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery cq = cb.createQuery(cls);
        Root root = cq.from(cls);

        //处理where条件
        wherePredicate(cb, cq, root, uriInfo);

//        String navigateName = entityNavigate.getEntityType().getFullQualifiedName().getFullQualifiedNameAsString();
//        Class naviCls = NumsgReflectionUtil.newClass(navigateName);
        //Root naviRoot = cq.from(naviCls);
        //cb.and(cb.equal(naviRoot.get("dept"),'a'));

        cq.select(cb.countDistinct(root));

        TypedQuery typedQuery = em.createQuery(cq);
        Object result = typedQuery.getSingleResult();

        int count = Integer.decode(result.toString());
        em.close();
        return count;
    }


    private List<Object> readDatabase(final UriInfo uriInfo, EdmEntitySet edmEntitySet, String naviFetch) throws ODataException {
        EntityManager em = entityManagerFactory.createEntityManager();
        String entityName = edmEntitySet.getEntityType().getFullQualifiedName().getFullQualifiedNameAsString();
        Class cls = NumsgReflectionUtil.newClass(entityName);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery cq = cb.createQuery(cls);
        Root root = cq.from(cls);
        List<String> fetches = getFetches(uriInfo, naviFetch);
        if (fetches != null && !fetches.isEmpty()) {
            for (String i : fetches) {
                root.fetch(i, JoinType.LEFT);
            }
        }

        //处理where条件
        wherePredicate(cb, cq, root, uriInfo);

        //处理orderby
        orderByPredicate(uriInfo, cb, cq, root);

        return skipTopPredicate(uriInfo, em, cq, edmEntitySet);
    }

    private List<String> getFetches(final UriInfo uriInfo, String fetch) {
        List<String> fetches = new ArrayList<>();
        ExpandOption expand = uriInfo.getExpandOption();
        if (expand != null) {
            for (ExpandItem expandItem : expand.getExpandItems()) {
                UriResource expandUriResource = expandItem.getResourcePath().getUriResourceParts().get(0);
                if (expandUriResource instanceof UriResourceNavigation) {
                    EdmNavigationProperty edmNavigationProperty = ((UriResourceNavigation) expandUriResource).getProperty();
                    String name = edmNavigationProperty.getName();
                    fetches.add(name);
                }
            }
        }
        if (fetch != null && !fetches.contains(fetch)) {
            fetches.add(fetch);
        }
        return fetches;
    }

    private void wherePredicate(CriteriaBuilder cb, CriteriaQuery cq, Root root, UriInfo uriInfo)
            throws ODataApplicationException, ExpressionVisitException {
        Predicate predicate = null;
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
        List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
        if (keyPredicates != null && !keyPredicates.isEmpty()) {
            //主键里面的过滤条件没有多重
            UriParameter i = keyPredicates.get(0);
            String strValue = i.getText();
            if (strValue.contains("'")) {
                strValue = strValue.replaceAll("'", "");
            }
            predicate = cb.equal(root.get(i.getName()), strValue);
        } else {
            FilterOption fo = uriInfo.getFilterOption();
            if (fo != null && fo.getExpression() != null) {
                Expression expr = fo.getExpression();
                NumsgFilterExpressionVisitor visitor = new NumsgFilterExpressionVisitor(cb, root, entityManagerFactory);
                predicate = (Predicate) expr.accept(visitor);
            }
        }
        if (predicate != null) {
            cq.where(predicate);
        }
    }

    private void orderByPredicate(UriInfo uriInfo, CriteriaBuilder cb, CriteriaQuery cq, Root root) {
        OrderByOption orderByOption = uriInfo.getOrderByOption();
        if (orderByOption != null) {
            List<OrderByItem> orderItemList = orderByOption.getOrders();
            List<Order> orders = new ArrayList<>();
            for (OrderByItem item : orderItemList) {
                Expression expression = item.getExpression();
                UriInfoResource resourcePath = ((Member) expression).getResourcePath();
                Order order;
                if (item.isDescending()) {
                    order = cb.desc(getOrderExpression(root, resourcePath));
                } else {
                    order = cb.asc(getOrderExpression(root, resourcePath));
                }
                orders.add(order);
            }
            cq.orderBy(orders);
        }
    }

    private Path getOrderExpression(Root root, UriInfoResource uriInfoResource) {
        List<UriResource> uriResources = uriInfoResource.getUriResourceParts();
        if (uriResources.size() > 1) {
            Path result;
            if (uriResources.get(0) instanceof UriResourceNavigation) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) uriResources.get(0);
                result = root.get(uriResourceNavigation.getProperty().getName());
                for (int i = 1; i < uriResources.size(); i++) {
                    if (uriResources.get(i) instanceof UriResourceNavigation) {
                        UriResourceNavigation urn = (UriResourceNavigation) uriResources.get(i);
                        result = result.get(urn.getProperty().getName());
                    } else if (uriResources.get(i) instanceof UriResourcePrimitiveProperty) {
                        UriResourcePrimitiveProperty urpProperty = (UriResourcePrimitiveProperty) uriResources.get(i);
                        result = result.get(urpProperty.getProperty().getName());
                    }
                }
                return result;
            }
            return null;
        } else {
            UriResource uriResource = uriResources.get(0);
            EdmProperty edmProperty = ((UriResourcePrimitiveProperty) uriResource).getProperty();
            String sortPropertyName = edmProperty.getName();
            return root.get(sortPropertyName);
        }
    }

    private List<Object> skipTopPredicate(UriInfo uriInfo, EntityManager em, CriteriaQuery cq, EdmEntitySet edmEntitySet) throws ODataApplicationException, ODataException {
        // handle $skip
        TypedQuery typedQuery = em.createQuery(cq);
        SkipOption skipOption = uriInfo.getSkipOption();
        int count = readDataCount(uriInfo,edmEntitySet ,null );
        if (skipOption != null) {
            int skipNumber = skipOption.getValue();
            if (skipNumber >= 0) {
                typedQuery.setFirstResult(skipNumber);
            } else {
                throw new ODataApplicationException("Invalid value for $skip", HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ROOT);
            }
        }else {
            if(count > 10){
                typedQuery.setFirstResult(0);
                typedQuery.setMaxResults(10);
            }
        }


        // handle $top
        TopOption topOption = uriInfo.getTopOption();
        if (topOption != null) {
            int topNumber = topOption.getValue();
            if (topNumber > 0) {
                typedQuery.setMaxResults(topNumber);
            } else {
                throw new ODataApplicationException("Invalid value for $top", HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ROOT);
            }
        }
        List result = typedQuery.getResultList();
        em.close();
        return result;
    }

    /*
    * 将数据库查询的结果集转换成可以展示的EntityCollection
    * */
    private EntityCollection ConvertEntityToOdataEntitySet(final UriInfo uriInfo, List<?> listEntity, EdmEntitySet edmEntitySet)
            throws ODataApplicationException, ParseException {
        EntityCollection entitySet = new EntityCollection();
        if (uriInfo.getCountOption() != null) {
            if (uriInfo.getCountOption().getText().equals("true")) {
                entitySet.setCount(listEntity.size());
                return entitySet;
            }
        }
        for (Object obEntity : listEntity) {
            Class reflectObj = obEntity.getClass();
            Field[] fields = reflectObj.getDeclaredFields();
            Entity entity = new Entity();
            Object objId = null;
            try {
                for (int i = 0; i < fields.length; i++) {
                    fields[i].setAccessible(true);
                    if(NumsgReflectionUtil.isODataColumn(fields[i])){
                        continue;
                    }
                    if (NumsgReflectionUtil.isHibernateId(fields[i])) {
                        objId = fields[i].get(obEntity);
                    }
                    Class fieldCls = fields[i].getType();
                    if (fieldCls.isLocalClass()) { //类
                        entity.addProperty(createComplex(fields[i].getName(), fields[i], fields[i].get(obEntity)));
                    } else if (fieldCls.isAssignableFrom(Date.class)) { //日期
                        Object data = fields[i].get(obEntity);
                        if (data != null) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            String timeStr = dateFormat.format(data);
                            data = dateFormat.parse(timeStr);
                        }
                        entity.addProperty(createPrimitive(fields[i].getName(), data));
                    } else if (fieldCls.isEnum()) { //枚举
                        entity.addProperty(createPrimitive(fields[i].getName(), fields[i].get(obEntity)));
                    } else {
                        entity.addProperty(createPrimitive(fields[i].getName(), fields[i].get(obEntity)));
                    }
                }
            } catch (IllegalAccessException ex) {
                throw new ODataApplicationException("ConvertEntityToOdataEntitySet: ", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
            }catch (ODataException ex) {
                throw new ODataApplicationException("ConvertEntityToOdataEntitySet: ", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
            }
            entity.setId(createId(edmEntitySet.getName(), objId));
            entity.setType(reflectObj.getName());
            entitySet.getEntities().add(entity);
        }
        return entitySet;
    }

    /*
    * 将数据库查询的结果集转换成可以展示的EntityCollection
    * */
    private EntityCollection ConvertEntityToOdataEntitySetX(final UriInfo uriInfo, List<?> listEntity, EdmEntitySet edmEntitySet)
            throws ODataApplicationException, ParseException {
        EntityCollection entitySet = new EntityCollection();
        for (Object obEntity : listEntity) {
            Class reflectObj = obEntity.getClass();
            Field[] fields = reflectObj.getDeclaredFields();
            Entity entity = new Entity();
            Object objId = null;
            try {
                for (int i = 0; i < fields.length; i++) {
                    fields[i].setAccessible(true);
                    if(NumsgReflectionUtil.isODataColumn(fields[i])){
                        continue;
                    }
                    if (NumsgReflectionUtil.isHibernateId(fields[i])) {
                        objId = fields[i].get(obEntity);
                    }
                    Class fieldCls = fields[i].getType();
                    if (fieldCls.isLocalClass()) { //类
                        entity.addProperty(createComplex(fields[i].getName(), fields[i], fields[i].get(obEntity)));
                    } else if (fieldCls.isAssignableFrom(Date.class)) { //日期
                        Object data = fields[i].get(obEntity);
                        if (data != null) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            String timeStr = dateFormat.format(data);
                            data = dateFormat.parse(timeStr);
                        }
                        entity.addProperty(createPrimitive(fields[i].getName(), data));
                    } else if (fieldCls.isEnum()) { //枚举
                        entity.addProperty(createPrimitive(fields[i].getName(), fields[i].get(obEntity)));
                    } else {
                        entity.addProperty(createPrimitive(fields[i].getName(), fields[i].get(obEntity)));
                    }
                }
            } catch (IllegalAccessException ex) {
                throw new ODataApplicationException("ConvertEntityToOdataEntitySet: ", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
            }catch (ODataException ex) {
                throw new ODataApplicationException("ConvertEntityToOdataEntitySet: ", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
            }
            entity.setId(createId(edmEntitySet.getName(), objId));
            entity.setType(reflectObj.getName());
            entitySet.getEntities().add(entity);
        }
        return entitySet;
    }

    public Entity getNavigationEntity(Entity entity, EdmEntityType relatedEntityType) {
        EntityCollection collection = getRelatedEntityCollection(entity, relatedEntityType);
        if (collection == null)
            return null;
        if (collection.getEntities().isEmpty()) {
            return null;
        }
        return collection.getEntities().get(0);
    }

    public Entity getRelatedEntity(Entity entity, EdmEntityType relatedEntityType) {
        EntityCollection collection = getRelatedEntityCollection(entity, relatedEntityType);
        if (collection == null)
            return null;
        if (collection.getEntities().isEmpty()) {
            return null;
        }
        return collection.getEntities().get(0);
    }

    public Entity getNavigationEntity(Entity entity, EdmEntityType relatedEntityType, List<UriParameter> keyPredicates) {
        EntityCollection relatedEntities = getRelatedEntityCollection(entity, relatedEntityType);
        return NumsgEntityUtil.findEntity(relatedEntityType, relatedEntities, keyPredicates);
    }

    public EntityCollection getRelatedEntityCollection(Entity sourceEntity, EdmEntityType targetEntityType) {
        EntityCollection navigationTargetEntityCollection = new EntityCollection();
        try {
            Class clazz = NumsgReflectionUtil.newClass(sourceEntity.getType());
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                if (NumsgReflectionUtil.isHibernateId(field)) {
                    navigationTargetEntityCollection.setId(createId(sourceEntity, field.getName(), targetEntityType.getName())); //"id"
                    break;
                }
            }
        } catch (ODataException ex) {
            throw new ODataRuntimeException("error: " + ex.getMessage(), ex);
        }

        String naviPropertyName = getNaviPropertyName(sourceEntity, targetEntityType);
        if (naviPropertyName != null) {
            Object sourceNavi = sourceEntity.getProperty(naviPropertyName).getValue();
            if (sourceNavi instanceof Iterable) {
                for (Object i : (Iterable) sourceNavi) {
                    Entity entity = navigationToEntity(i, targetEntityType);
                    navigationTargetEntityCollection.getEntities().add(entity);
                }
            } else {
                Entity entity = navigationToEntity(sourceNavi, targetEntityType);
                navigationTargetEntityCollection.getEntities().add(entity);
            }
        }

        if (navigationTargetEntityCollection.getEntities().isEmpty()) {
            return null;
        }
        return navigationTargetEntityCollection;
    }

    private String getNaviPropertyName(Entity sourceEntity, EdmEntityType targetEntityType) {
        String targeEntityName = targetEntityType.getFullQualifiedName().getFullQualifiedNameAsString();
        String naviPropertyName = null;
        String pbName = PersistentBag.class.getName();
        List<Property> properties = sourceEntity.getProperties();
        for (Property i : properties) {
            if (i.getValue() == null)
                continue;
            Object value = i.getValue();
            Class cls = value.getClass();

            String name;
            if (cls.getName().contains(pbName)) {
                //集合导航属性
                PersistentBag pb = (PersistentBag) value;
                if (!pb.isEmpty()) {
                    name = pb.get(0).getClass().getName();
                    if (name.contains(targeEntityName)) {
                        naviPropertyName = i.getName();
                        break;
                    }
                }
            } else {
                name = cls.getTypeName();
                if (name.contains(targeEntityName)) {
                    naviPropertyName = i.getName();
                    break;
                }
            }
        }
        return naviPropertyName;
    }

    public EntityCollection getNavigationEntityCollection(Entity sourceEntity, EdmEntityType targetEntityType) {
        EntityCollection navigationTargetEntityCollection = new EntityCollection();
        String naviPropertyName = getNaviPropertyName(sourceEntity, targetEntityType);
        if (naviPropertyName != null) {
            Object sourceNavi = sourceEntity.getProperty(naviPropertyName).getValue();
            if (sourceNavi instanceof PersistentBag) {
                for (Object i : (PersistentBag) sourceNavi) {
                    Entity entity = navigationToEntity(i, targetEntityType);
                    navigationTargetEntityCollection.getEntities().add(entity);
                }
            }
        }
        return navigationTargetEntityCollection;
    }

    private Entity navigationToEntity(Object sourceNavi, EdmEntityType targetEntityType) {
        Entity entity = new Entity();
        Object objId = null;
        for (Field field : sourceNavi.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object filedValue = field.get(sourceNavi);
                entity.addProperty(createPrimitive(field.getName(), filedValue));
                if (NumsgReflectionUtil.isHibernateId(field)) {
                    objId = filedValue;
                }
            } catch (IllegalAccessException ex) {
                throw new ODataRuntimeException("error: " + ex.getMessage(), ex);
            }
        }

        entity.setId(createId(targetEntityType.getName(), objId));
        String nms = targetEntityType.getNamespace();
        entity.setType(new FullQualifiedName(nms, targetEntityType.getName()).getFullQualifiedNameAsString());
        return entity;
    }


    private Property createComplex(final String name, Field field, final Object value) {
        ComplexValue complexValue = new ComplexValue();
        List<Property> complexProperties = complexValue.getValue();
        complexProperties.add(createPrimitive(name, value));

        return new Property(null, field.getName(), ValueType.COMPLEX, complexValue);
    }

    private Property createPrimitive(final String name, final Object value) {
        return new Property(null, name, ValueType.PRIMITIVE, value);
    }

    private URI createId(String entitySetName, Object id) {
        try {
            return new URI(entitySetName + "(" + String.valueOf(id) + ")");
        } catch (URISyntaxException e) {
            throw new ODataRuntimeException("Unable to create id for entity: " + entitySetName, e);
        }
    }

    private URI createId(Entity entity, String idPropertyName, String navigationName) {
        try {
            StringBuilder sb = new StringBuilder(getEntitySetName(entity)).append("(");
            final Property property = entity.getProperty(idPropertyName);
            sb.append(property.asPrimitive()).append(")");
            if (navigationName != null) {
                sb.append("/").append(navigationName);
            }
            return new URI(sb.toString());
        } catch (URISyntaxException e) {
            throw new ODataRuntimeException("Unable to create id for entity: " + entity, e);
        }
    }

    private String getEntitySetName(Entity entity) {
        return entity.getType();
    }
}
