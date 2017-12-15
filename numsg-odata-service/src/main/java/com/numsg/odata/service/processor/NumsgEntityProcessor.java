package com.numsg.odata.service.processor;


import com.numsg.odata.service.datasource.NumsgDataProvider;
import com.numsg.odata.service.datasource.option.ExpandOptionBuilder;
import com.numsg.odata.service.util.NumsgEntityUtil;
import com.numsg.odata.service.util.NumsgProcessorUtil;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.EntityProcessor;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;

import java.util.List;
import java.util.Locale;

/**
 * Created by gaoqiang on 2017/4/26.
 */
public class NumsgEntityProcessor implements EntityProcessor {

    private OData odata;
    private ServiceMetadata serviceMetadata;
    private NumsgDataProvider dataProvider;

    public NumsgEntityProcessor(NumsgDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {

        EdmEntityType responseEdmEntityType = null; // 用于构建 ContextURL
        Entity responseEntity = null;
        EdmEntitySet responseEdmEntitySet = null; // 用于构建 ContextURL

        // 1. 首先确认是进入哪个EntitySet
        final EdmEntitySet edmFirstEntitySet = NumsgProcessorUtil.getFirstEdmEntitySet(uriInfo.asUriInfoResource());

        // 2.获取URI资源集合
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        int segmentCount = resourceParts.size();

        // 3. 分析URI segmentCount=1 表示没有查导航属性
        responseEntity = new Entity();

        if (segmentCount == 1) {
            responseEdmEntitySet = edmFirstEntitySet;
            List<Entity> entities =  dataProvider.read(uriInfo, edmFirstEntitySet,null).getEntities();
            if(entities.size() > 0) {
                responseEntity = entities.get(0);
            }
        }else if(segmentCount == 2) {
            UriResource navSegment = resourceParts.get(1); // in our example we don't support more complex URIs
            if (navSegment instanceof UriResourceNavigation) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) navSegment;
                EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                responseEdmEntityType = edmNavigationProperty.getType();
                // contextURL最后一段
                responseEdmEntitySet = NumsgEntityUtil.getNavigationTargetEntitySet(edmFirstEntitySet, edmNavigationProperty);                String naviName = uriResourceNavigation.getSegmentValue();
                List<Entity> entities = dataProvider.read(uriInfo, edmFirstEntitySet ,naviName).getEntities();
                if(entities.size() > 0) {
                    Entity sourceEntity = entities.get(0);
                    //获取主键过滤条件
                    List<UriParameter> navKeyPredicates = uriResourceNavigation.getKeyPredicates();
                    // 检查导航属性是to-one or to-many
                    if (navKeyPredicates.isEmpty()) { // e.g. OdataService.svc/Products(1)/Category
                        responseEntity = dataProvider.getNavigationEntity(sourceEntity, responseEdmEntityType);
                    } else { // e.g. OdataService.svc/Categories(3)/Products(5)
                        responseEntity = dataProvider.getNavigationEntity(sourceEntity, responseEdmEntityType, navKeyPredicates);
                    }
                }
            }
        }else {
            throw new ODataApplicationException("Only EntitySet is supported",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
        if (responseEntity == null) {
            // 目前只支持. OdataService.svc/Categories(4) or OdataService.svc/Categories(3)/Products(999)
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        }

        // 4. 使用system query options
        // handle $select 使用默认实现
        SelectOption selectOption = uriInfo.getSelectOption();
        // handle $expand
        ExpandOption expandOption = uriInfo.getExpandOption();
        ExpandOptionBuilder.handlerExpandOption(expandOption, responseEntity ,responseEdmEntitySet, dataProvider);

        // 5.  序列化serialize
        EdmEntityType edmEntityType = responseEdmEntitySet.getEntityType();
        String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption, selectOption);
        ContextURL contextUrl = ContextURL.with().entitySet(responseEdmEntitySet)
                .selectList(selectList)
                .suffix(ContextURL.Suffix.ENTITY).build();

        // 确保序列化时考虑到$expand and $selec
        // 设置序列化的option
        EntitySerializerOptions opts = EntitySerializerOptions.with()
                .contextURL(contextUrl)
                .select(selectOption)
                .expand(expandOption)
                .build();

        ODataSerializer serializer = this.odata.createSerializer(responseFormat);
        SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntityType, responseEntity, opts);

        // 6. 设置respons 对象
        response.setContent(serializerResult.getContent());
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());

    }



    @Override
    public void createEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {

    }

    @Override
    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {

    }

    @Override
    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {

    }
}
