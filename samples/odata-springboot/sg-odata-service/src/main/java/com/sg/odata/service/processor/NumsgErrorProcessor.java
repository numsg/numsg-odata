package com.sg.odata.service.processor;

import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.ErrorProcessor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * Created by gaoqiang on 2017/4/27.
 */

/*
* 统一错误处理
* */
public class NumsgErrorProcessor implements ErrorProcessor {

    private OData odata;
    private ServiceMetadata serviceMetadata;

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void processError(ODataRequest request, ODataResponse response, ODataServerError serverError, ContentType responseFormat) {
        try {
            String errorMessage = "{ \"statusCode\": %s, \"message\":\"%s\"}";
            errorMessage = String.format(errorMessage,serverError.getStatusCode(),serverError.getMessage() );
            InputStream   errorStream   =   new  ByteArrayInputStream(errorMessage.getBytes("UTF-8"));
            response.setContent(errorStream);
            response.setStatusCode(serverError.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }finally {

        }
    }

}
