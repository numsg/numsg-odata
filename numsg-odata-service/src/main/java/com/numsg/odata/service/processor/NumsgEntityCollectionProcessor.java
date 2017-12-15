package com.numsg.odata.service.processor;

import com.numsg.odata.service.datasource.NumsgDataProvider;
import com.numsg.odata.service.datasource.option.ExpandOptionBuilder;
import com.numsg.odata.service.util.NumsgEntityUtil;
import com.numsg.odata.service.util.NumsgProcessorUtil;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.CountEntityCollectionProcessor;
import org.apache.olingo.server.api.processor.EntityCollectionProcessor;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.CountOption;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Created by gaoqiang on 2017/4/26.
 */
public class NumsgEntityCollectionProcessor implements EntityCollectionProcessor,CountEntityCollectionProcessor {
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private NumsgDataProvider dataProvider;

    public NumsgEntityCollectionProcessor(NumsgDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }


    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void countEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {
        // 1. 首先确认是进入哪个EntitySet
        final EdmEntitySet edmFirstEntitySet = NumsgProcessorUtil.getFirstEdmEntitySet(uriInfo.asUriInfoResource());

        //2.使用spring jpa-data 获取数据
        int count = dataProvider.readEntityCount(uriInfo, edmFirstEntitySet,edmFirstEntitySet);
        String str = String.valueOf(count);
        InputStream serializedContent = new ByteArrayInputStream(str.getBytes());

        //设置 response data, headers and status code
        response.setContent(serializedContent);
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, "application/json;charset:utf-8");
    }

    @Override
    public void readEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        // 获取URI资源集合
        EntityCollection entityCollection = null;
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        int segmentCount = resourceParts.size();

        UriResource uriResource = resourceParts.get(0); // in our example, the first segment is the EntitySet
        if (!(uriResource instanceof UriResourceEntitySet)) {
            throw new ODataApplicationException("Only EntitySet is supported",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
        }

        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
        EdmEntitySet edmFirstEntitySet = uriResourceEntitySet.getEntitySet();
        EdmEntitySet responseEdmEntitySet = null;

        //如果是获取数量，则不查询数据实体
        CountOption countOption = uriInfo.getCountOption();
        boolean isCount = false;
        if (countOption != null) {
            isCount = countOption.getValue();
        }

        if (isCount) {
            if (segmentCount == 1) {
                //直接查询实体
                responseEdmEntitySet = edmFirstEntitySet;
            } else if (segmentCount == 2) {
                UriResource lastSegment = resourceParts.get(1); // don't support more complex URIs
                if (lastSegment instanceof UriResourceNavigation) {
                    UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) lastSegment;
                    EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                    responseEdmEntitySet = NumsgEntityUtil.getNavigationTargetEntitySet(edmFirstEntitySet, edmNavigationProperty);
                }
            }
            int count = dataProvider.readEntityCount(uriInfo, edmFirstEntitySet,responseEdmEntitySet);
            entityCollection = new EntityCollection();
            entityCollection.setCount(count);
            EntityCollection eCollection = dataProvider.readX(uriInfo, edmFirstEntitySet, null);
            for(Entity entity :eCollection.getEntities()){
                entityCollection.getEntities().add(entity);
            }
        }else {

            //2.使用spring jpa-data 获取数据
            if (segmentCount == 1) {
                //直接查询实体
                responseEdmEntitySet = edmFirstEntitySet;
                entityCollection = dataProvider.read(uriInfo, edmFirstEntitySet, null);
            } else if (segmentCount == 2) {
                UriResource lastSegment = resourceParts.get(1); // don't support more complex URIs
                if (lastSegment instanceof UriResourceNavigation) {
                    UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) lastSegment;
                    EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                    EdmEntityType targetEntityType = edmNavigationProperty.getType();
                    responseEdmEntitySet = NumsgEntityUtil.getNavigationTargetEntitySet(edmFirstEntitySet, edmNavigationProperty);

                    // 2nd: fetch the data from backend
                    Entity sourceEntity = dataProvider.read(uriInfo, edmFirstEntitySet, null).getEntities().get(0);

                    // error handling for e.g. DemoService.svc/Categories(99)/Products
                    if (sourceEntity == null) {
                        throw new ODataApplicationException("Entity not found.",
                                HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
                    }
                    //获取导航属性集合
                    entityCollection = dataProvider.getNavigationEntityCollection(sourceEntity, targetEntityType);
                }
            } else {
                throw new ODataApplicationException("Only EntitySet is supported",
                        HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
            }
        }

        // 3.使用客户端请求的requestedContentType来创建序列化格式
        ODataSerializer serializer = odata.createSerializer(responseFormat);

        // 4. 使用system query options
        // 处理 $expand
        final ExpandOption expandOption = uriInfo.getExpandOption();
        for(Entity entity :entityCollection.getEntities()){
            ExpandOptionBuilder.handlerExpandOption(expandOption, entity ,responseEdmEntitySet, dataProvider);
        }

        // select 默认处理
        final SelectOption selectOption = uriInfo.getSelectOption();
        String selectList = odata.createUriHelper().buildContextURLSelectList(responseEdmEntitySet.getEntityType(), expandOption, selectOption);

        ContextURL contextUrl = ContextURL.with().entitySet(responseEdmEntitySet)
                .selectList(selectList)
                .suffix(ContextURL.Suffix.ENTITY).build();

        final String id = request.getRawBaseUri() + "/" + responseEdmEntitySet.getName();
        // 5.开始执行序列化
        InputStream serializedContent = serializer.entityCollection(serviceMetadata, responseEdmEntitySet.getEntityType(), entityCollection,
                EntityCollectionSerializerOptions.with()
                        .id(id)
                        .contextURL(contextUrl)
                        .count(uriInfo.getCountOption())
                        .expand(expandOption).select(selectOption)
                        .build()).getContent();

        // 5.设置response data，headers and status code
        response.setContent(serializedContent);
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }
}
