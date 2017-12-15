package com.numsg.odata.service.edm;

import com.numsg.odata.service.enumx.EntityRelationType;
import com.numsg.odata.service.util.NumsgEntityTypeUtil;
import com.numsg.odata.service.util.NumsgEnumUtil;
import com.numsg.odata.service.util.NumsgReflectionUtil;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.hibernate.jpa.internal.metamodel.AbstractType;
import org.hibernate.jpa.internal.metamodel.EntityTypeImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by gaoqiang on 2017/4/26.
 */

@Service
public class NumsgEdmProvider extends CsdlAbstractEdmProvider {

    private final String NAMESPACE = "com.numsg" ;

    // EDM Container
    public final String CONTAINER_NAME = "NumsgContainer";

    public FullQualifiedName CONTAINER_FQN = new FullQualifiedName(NAMESPACE, CONTAINER_NAME);

    @Autowired
    private EntityManagerFactory entityManagerFactory;
    private Annotation annotation;


    @Override
    public CsdlEntityType getEntityType(FullQualifiedName entityTypeName) throws ODataException {
        EntityType entityType = NumsgEntityTypeUtil.getEntityTypeByEntityFullTypeName(entityManagerFactory.getMetamodel()
                ,entityTypeName.getFullQualifiedNameAsString());

        CsdlEntityType csdlEntityType = new CsdlEntityType()
                .setName(entityType.getName())
                .setKey(Arrays.asList(
                        new CsdlPropertyRef().setName(NumsgEntityTypeUtil.getId(entityType))))
                .setProperties(
                        createCsdlPropertyList(entityType)
                ).setNavigationProperties(
                        createNavigationProperties(entityType)
                );
        return  csdlEntityType;
    }

    @Override
    public CsdlEntitySet getEntitySet(FullQualifiedName entityContainer, String entitySetName) throws ODataException {
        if (!CONTAINER_FQN.equals(entityContainer)) {
            throw new ODataException("Container not equal");
        }
        Metamodel metamodel = entityManagerFactory.getMetamodel();
        String entitySetUrlName = entitySetName;
        if(entitySetName.endsWith("ves")){
            int index = entitySetName.length();
            entitySetUrlName = entitySetName.substring(0,index - 3);
        }else if(entitySetName.endsWith("ies")){
            int index = entitySetName.length();
            entitySetUrlName = entitySetName.substring(0,index - 3);
        }else if(entitySetName.endsWith("es")){
            int index = entitySetName.length();
            entitySetUrlName = entitySetName.substring(0,index - 3);
        }else if(entitySetName.endsWith("s")){
            int index = entitySetName.length();
            entitySetUrlName = entitySetName.substring(0,index - 1);
        }

        List<String> entityNames =  metamodel.getEntities().stream().map(EntityType::getName).collect(Collectors.toList());
        String entitySetUrlNameLower = entitySetUrlName.toLowerCase();
        List<String> finder = entityNames.stream().filter(n->n.toLowerCase().contains(entitySetUrlNameLower)).collect(Collectors.toList());
        if(finder == null || finder.size() > 1){
            throw new ODataException("Not find entitySet");
        }
        String entityName = finder.get(0);
        EntityType entityType = metamodel.getEntities().stream().filter(n->n.getName().equals(entityName)).findFirst().get();
        if(NumsgReflectionUtil.isODataEntity(entityType)){
            throw new ODataException("Not find entitySet");
        }
        return CreateCsdlEntitySet(entityType,entityName);
    }

    @Override
    public CsdlEntityContainerInfo getEntityContainerInfo(FullQualifiedName entityContainerName) throws ODataException {
        if (entityContainerName == null || CONTAINER_FQN.equals(entityContainerName)) {
            return new CsdlEntityContainerInfo().setContainerName(CONTAINER_FQN);
        }
        throw new ODataException("Get EntityContainerInfo null");
    }

    @Override
    public CsdlEntityContainer getEntityContainer() throws ODataException {
        CsdlEntityContainer container = new CsdlEntityContainer();
        container.setName(CONTAINER_FQN.getName());

        // EntitySets
        List<CsdlEntitySet> entitySets = new ArrayList<CsdlEntitySet>();
        container.setEntitySets(entitySets);
        Metamodel metamodel = entityManagerFactory.getMetamodel();
        for (EntityType entityType : metamodel.getEntities()) {
            String entityName = ((EntityTypeImpl) entityType).getTypeName().substring(((EntityTypeImpl) entityType).getTypeName().lastIndexOf('.')+1);
            //entitySets.add(getEntitySet(CONTAINER_FQN, entityName+"s"));
            entitySets.add(getEntitySet(CONTAINER_FQN, entityName));
        }
        return container;
    }

    @Override
    public CsdlComplexType getComplexType(FullQualifiedName complexTypeName) throws ODataException {
        Class clazz = NumsgReflectionUtil.newClass(complexTypeName.getFullQualifiedNameAsString());
        CsdlComplexType csdlComplexType = new CsdlComplexType();
        List<CsdlProperty> csdlPropertyList = new ArrayList<>();
        for(Field field: clazz.getDeclaredFields() ) {
            csdlPropertyList.add(new CsdlProperty()
                    .setName(field.getName())
                    .setType(EdmPrimitiveTypeKind.Int32.getFullQualifiedName()));

        }
        csdlComplexType.setName(complexTypeName.getName()).setProperties(csdlPropertyList);
        return csdlComplexType;
    }

    @Override
    public List<CsdlSchema> getSchemas() throws ODataException {
        List<CsdlSchema> schemas = new ArrayList<CsdlSchema>();
        CsdlSchema schema = new CsdlSchema();
        schema.setNamespace(NAMESPACE);
        List<CsdlEntityType> entityTypes = new ArrayList<CsdlEntityType>();
        Metamodel metamodel = entityManagerFactory.getMetamodel();
        for (EntityType entityType : metamodel.getEntities()) {
//            String entityName = ((EntityTypeImpl) entityType).getTypeName().substring(((EntityTypeImpl) entityType).getTypeName().lastIndexOf('.')+1);
//            entityTypes.add(getEntityType( new FullQualifiedName(NAMESPACE, entityName)));
            if(NumsgReflectionUtil.isODataEntity(entityType)){
                continue;
            }
            String entityName = ((EntityTypeImpl) entityType).getTypeName();
            entityTypes.add(getEntityType( new FullQualifiedName(entityName)));
        }
        schema.setEntityTypes(entityTypes);
        schema.setEntityContainer(getEntityContainer());
        schemas.add(schema);
        return schemas;
    }

    @Override
    public CsdlEnumType getEnumType(FullQualifiedName enumTypeName) throws ODataException {
        return super.getEnumType(enumTypeName);
    }

    /*
            * 创建CsdlEntity的普通属性
            * @eg new CsdlProperty().setName("ModelYear").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName())
            *   .setMaxLength(4),
            * */
    private List<CsdlProperty> createCsdlPropertyList(EntityType entityType) throws ODataException {

        List<CsdlProperty> csdlPropertyList = new ArrayList<>();
        Class clazz = NumsgReflectionUtil.newClass(((AbstractType)entityType).getTypeName());

        for(Field field: clazz.getDeclaredFields() ) {
            if(NumsgReflectionUtil.isODataColumn(field)){
                continue;
            }
            if(NumsgReflectionUtil.isHibernateCloumn(field)){

                CsdlProperty csdlProperty = new CsdlProperty();
                if(NumsgEntityTypeUtil.getEdmType(field.getType())!=null){
                    EdmPrimitiveTypeKind kd = NumsgEntityTypeUtil.getEdmType(field.getType());
                    csdlProperty.setName(field.getName()).setType(NumsgEntityTypeUtil.getEdmType(field.getType()).getFullQualifiedName());
                }else {
                    if(field.getType().isEnum()) {
                        NumsgEnumUtil.cacheEnum(field.getType());
                        csdlProperty.setName(field.getName()).setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());
                    }
                    else {
                        csdlProperty.setName(field.getName()).setType(NumsgEntityTypeUtil.getEdmType(field.getType()).getFullQualifiedName());
                    }
                }
                csdlPropertyList.add(csdlProperty);
            }
        }

        return  csdlPropertyList;
    }

    /*
    * 创建CsdlEntity的导航属性
    * @eg   new CsdlNavigationProperty().setName("Manufacturer").setType(ET_MANUFACTURER)
    * */
    private List<CsdlNavigationProperty> createNavigationProperties(EntityType entityType) throws ODataException {
        List<CsdlNavigationProperty> csdlNavigationPropertyList = new ArrayList<>();
        Class clazz = NumsgReflectionUtil.newClass(((AbstractType)entityType).getTypeName());

        for(Field field: clazz.getDeclaredFields() ) {
            if(NumsgReflectionUtil.getHibernateRelationType(field).equals(EntityRelationType.ManyToMany)
                    || NumsgReflectionUtil.getHibernateRelationType(field).equals(EntityRelationType.OneToMany)){
                CsdlNavigationProperty csdlNavigationProperty = new CsdlNavigationProperty();
                ParameterizedType pt = (ParameterizedType) field.getGenericType();
                csdlNavigationProperty
                        .setName(field.getName())
                        .setType(new FullQualifiedName(pt.getActualTypeArguments()[0].getTypeName()))
                        .setCollection(true);
                csdlNavigationPropertyList.add(csdlNavigationProperty);
                continue;
            }else if(NumsgReflectionUtil.getHibernateRelationType(field).equals(EntityRelationType.ManyToOne)
                    ||NumsgReflectionUtil.getHibernateRelationType(field).equals(EntityRelationType.OneToOne)){
                CsdlNavigationProperty csdlNavigationProperty = new CsdlNavigationProperty();
                String nms = field.getType().getTypeName();
                csdlNavigationProperty
                        .setName(field.getName())
                        //.setType(new FullQualifiedName(NAMESPACE, field.getName()));
                        .setType(new FullQualifiedName(nms));
                csdlNavigationPropertyList.add(csdlNavigationProperty);
                continue;
            }
        }
        return  csdlNavigationPropertyList;
    }

    /*
    * 创建CsdlEntitySet
    * */
    private CsdlEntitySet CreateCsdlEntitySet(EntityType entityType,String entitySetName) throws ODataException {
        List<CsdlNavigationPropertyBinding> csdlNavigationPropertyBindingList = new ArrayList<>();
        Class clazz = NumsgReflectionUtil.newClass(((AbstractType)entityType).getTypeName());
        CONTAINER_FQN = new FullQualifiedName(NAMESPACE, CONTAINER_NAME);;
        for(Field field: clazz.getDeclaredFields() ) {
            if(NumsgReflectionUtil.isODataColumn(field)){
                continue;
            }
            if(NumsgReflectionUtil.getHibernateRelationType(field).equals(EntityRelationType.ManyToMany)
                    || NumsgReflectionUtil.getHibernateRelationType(field).equals(EntityRelationType.OneToMany)){
                CsdlNavigationPropertyBinding csdlNavigationPropertyBinding =new CsdlNavigationPropertyBinding();
                csdlNavigationPropertyBinding
                        .setPath(field.getName())
                        .setTarget(CONTAINER_FQN.getFullQualifiedNameAsString() + "/" + field.getName());
                csdlNavigationPropertyBindingList.add(csdlNavigationPropertyBinding);
                continue;
            }else if(NumsgReflectionUtil.getHibernateRelationType(field).equals(EntityRelationType.ManyToOne)
                    ||NumsgReflectionUtil.getHibernateRelationType(field).equals(EntityRelationType.OneToOne)){
                CsdlNavigationPropertyBinding csdlNavigationPropertyBinding =new CsdlNavigationPropertyBinding();
                csdlNavigationPropertyBinding
                        .setPath(field.getName())
                        //.setTarget(CONTAINER_FQN.getFullQualifiedNameAsString() + "/" + field.getName()+"s");
                        .setTarget(CONTAINER_FQN.getFullQualifiedNameAsString() + "/" + field.getName());
                csdlNavigationPropertyBindingList.add(csdlNavigationPropertyBinding);
                continue;
            }
        }
        CsdlEntitySet csdlEntitySet = new CsdlEntitySet();
        String nms = ((AbstractType) entityType).getTypeName();
        csdlEntitySet
                .setName(entitySetName)
                .setType(nms)
                //.setType( new FullQualifiedName(NAMESPACE, entitySetName.substring(0,entitySetName.length()-1)) )
                .setNavigationPropertyBindings(csdlNavigationPropertyBindingList);
        return  csdlEntitySet;

    }
}
