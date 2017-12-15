package com.numsg.odata.service.processor;

import com.numsg.odata.service.datasource.NumsgDataProvider;
import com.numsg.odata.service.util.NumsgProcessorUtil;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmComplexType;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.PrimitiveProcessor;
import org.apache.olingo.server.api.processor.PrimitiveValueProcessor;
import org.apache.olingo.server.api.serializer.ComplexSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.PrimitiveSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResourceProperty;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;

import java.io.InputStream;
import java.util.Locale;

/**
 * Created by gaoqiang on 2017/4/28.
 */
public class NumsgPrimitiveProcessor implements PrimitiveProcessor, PrimitiveValueProcessor {

    private OData odata;
    private ServiceMetadata serviceMetadata;
    private final NumsgDataProvider dataProvider;

    public NumsgPrimitiveProcessor(final NumsgDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readPrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        readProperty(response, uriInfo, responseFormat, false);
    }

    @Override
    public void updatePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {

    }

    @Override
    public void deletePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {

    }

    @Override
    public void readPrimitiveValue(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {

    }

    @Override
    public void updatePrimitiveValue(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {

    }

    @Override
    public void deletePrimitiveValue(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {

    }

    /*
    * readProperty
    * */
    private void readProperty(ODataResponse response, UriInfo uriInfo, ContentType contentType,
                              boolean complex) throws ODataApplicationException, SerializerException {
        // 1. 首先确认是进入哪个EntitySet
        final EdmEntitySet edmEntitySet = NumsgProcessorUtil.getFirstEdmEntitySet(uriInfo.asUriInfoResource());
        // 2.使用spring jpa-data 获取数据
        Entity entity = dataProvider.read(uriInfo,edmEntitySet,null).getEntities().get(0);
        if (entity == null) {
            throw new ODataApplicationException("No entity found for this key",
                    HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        } else {
            // 3.查找需要进行序列化的属性值
            UriResourceProperty uriProperty = (UriResourceProperty) uriInfo
                    .getUriResourceParts().get(uriInfo.getUriResourceParts().size() - 1);
            EdmProperty edmProperty = uriProperty.getProperty();
            Property property = entity.getProperty(edmProperty.getName());
            if (property == null) {
                throw new ODataApplicationException("No property found",
                        HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
            }
//            if (property.getValue() == null) {
//                response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
//            } else {
            ODataSerializer serializer = odata.createSerializer(contentType);
            final ContextURL contextURL = isODataMetadataNone(contentType) ? null :
                    getContextUrl(edmEntitySet, true, null, null, edmProperty.getName());
            // 4.执行序列化
            InputStream serializerContent = complex ?
                    serializer.complex(serviceMetadata, (EdmComplexType) edmProperty.getType(), property,
                            ComplexSerializerOptions.with().contextURL(contextURL).build()).getContent() :
                    serializer.primitive(serviceMetadata, (EdmPrimitiveType) edmProperty.getType(), property,
                            PrimitiveSerializerOptions.with()
                                    .contextURL(contextURL)
                                    .scale(edmProperty.getScale())
                                    .nullable(edmProperty.isNullable())
                                    .precision(edmProperty.getPrecision())
                                    .maxLength(edmProperty.getMaxLength())
                                    .unicode(edmProperty.isUnicode()).build()).getContent();
            response.setContent(serializerContent);
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
//            }
        }
    }

    private ContextURL getContextUrl(final EdmEntitySet entitySet, final boolean isSingleEntity,
                                     final ExpandOption expand, final SelectOption select, final String navOrPropertyPath)
            throws SerializerException {

        return ContextURL.with().entitySet(entitySet)
                .selectList(odata.createUriHelper().buildContextURLSelectList(entitySet.getEntityType(), expand, select))
                .suffix(isSingleEntity ? ContextURL.Suffix.ENTITY : null)
                .navOrPropertyPath(navOrPropertyPath)
                .build();
    }

    public static boolean isODataMetadataNone(final ContentType contentType) {
        return contentType.isCompatible(ContentType.APPLICATION_JSON)
                && ContentType.VALUE_ODATA_METADATA_NONE.equalsIgnoreCase(
                contentType.getParameter(ContentType.PARAMETER_ODATA_METADATA));
    }
}
