package com.sg.odata.service;

import com.sg.odata.service.datasource.NumsgDataProvider;
import com.sg.odata.service.edm.NumsgEdmProvider;
import com.sg.odata.service.processor.NumsgEntityCollectionProcessor;
import com.sg.odata.service.processor.NumsgEntityProcessor;
import com.sg.odata.service.processor.NumsgErrorProcessor;
import com.sg.odata.service.processor.NumsgPrimitiveProcessor;
import org.springframework.util.StreamUtils;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.apache.olingo.commons.api.edmx.EdmxReference;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.core.ODataHandlerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.*;

/**
 * Created by gaoqiang on 2017/8/1.
 */
/*
 * 处理OData相关的请求
 */
public abstract class NumsgODataController {

    private static String URI = ""; //OdataService.svc/

    /** The split. */
    private int split = 0;

    /*
    * 日志记录组件
    * */
    private static final Logger LOG = LoggerFactory.getLogger(NumsgODataController.class);

    /*
    * EDM提供器
    * */
    @Autowired
    private NumsgEdmProvider numsgEdmProvider;

    /*
    * 数据提供器
    * */
    @Autowired
    private NumsgDataProvider numsgDataProvider;

    @RequestMapping(method = {
            GET, POST, PATCH, PUT, DELETE
    })
    protected ResponseEntity<String> service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
            throws ServletException, IOException,Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Start processing request from: {}", servletRequest.getRemoteAddr());
        }
        try {
            this.URI = servletRequest.getRequestURI().split("/")[1]+ '/';
            OData odata = OData.newInstance();
            ServiceMetadata edm = odata.createServiceMetadata(numsgEdmProvider, new ArrayList<EdmxReference>());
            ODataHandler handler = odata.createRawHandler(edm);

            handler.register(new NumsgEntityProcessor(numsgDataProvider));
            handler.register(new NumsgEntityCollectionProcessor(numsgDataProvider));
            handler.register(new NumsgPrimitiveProcessor(numsgDataProvider));
            handler.register(new NumsgErrorProcessor());
            ODataResponse response = handler.process(createODataRequest(servletRequest, split));

            String responseStr = StreamUtils.copyToString(
                    response.getContent(), Charset.defaultCharset());
            MultiValueMap<String, String> headers = new HttpHeaders();
            for (String key : response.getAllHeaders().keySet()) {
                if(key.equals("Content-Type")) {
                    headers.add(key, "application/json");
                }else{
                    headers.add(key, response.getAllHeaders().get(key).toString());
                }
            }
            return new ResponseEntity<String>(responseStr, headers,
                    HttpStatus.valueOf(response.getStatusCode()));
        } catch (RuntimeException e) {
            LOG.error("Server Error", e);
            throw new ServletException(e);
        }
    }

    /**
     * Creates the o data request.
     *
     * @param httpRequest
     *            the http request
     * @param split
     *            the split
     * @return the o data request
     * @throws Exception
     *             the o data translated exception
     */
    private ODataRequest createODataRequest(
            final HttpServletRequest httpRequest, final int split)
            throws Exception {
        try {
            ODataRequest odRequest = new ODataRequest();

            odRequest.setBody(httpRequest.getInputStream());
            extractHeaders(odRequest, httpRequest);
            extractMethod(odRequest, httpRequest);
            extractUri(odRequest, httpRequest, split);

            return odRequest;
        } catch (final IOException e) {
            throw new SerializerException("An I/O exception occurred.", e,
                    SerializerException.MessageKeys.IO_EXCEPTION);
        }
    }

    /**
     * Extract uri.
     *
     * @param odRequest
     *            the od request
     * @param httpRequest
     *            the http request
     * @param split
     *            the split
     */
    private void extractUri(final ODataRequest odRequest,
                            final HttpServletRequest httpRequest, final int split) {
        String rawRequestUri = httpRequest.getRequestURL().toString();

        String rawODataPath;
        if (!"".equals(httpRequest.getServletPath())) {
            int beginIndex;
            beginIndex = rawRequestUri.indexOf(URI);
            beginIndex += URI.length();
            rawODataPath = rawRequestUri.substring(beginIndex);
        } else if (!"".equals(httpRequest.getContextPath())) {
            int beginIndex;
            beginIndex = rawRequestUri.indexOf(httpRequest.getContextPath());
            beginIndex += httpRequest.getContextPath().length();
            rawODataPath = rawRequestUri.substring(beginIndex);
        } else {
            rawODataPath = httpRequest.getRequestURI();
        }

        String rawServiceResolutionUri;
        if (split > 0) {
            rawServiceResolutionUri = rawODataPath;
            for (int i = 0; i < split; i++) {
                int e = rawODataPath.indexOf("/", 1);
                if (-1 == e) {
                    rawODataPath = "";
                } else {
                    rawODataPath = rawODataPath.substring(e);
                }
            }
            int end = rawServiceResolutionUri.length() - rawODataPath.length();
            rawServiceResolutionUri = rawServiceResolutionUri.substring(0, end);
        } else {
            rawServiceResolutionUri = null;
        }

        String rawBaseUri = rawRequestUri.substring(0, rawRequestUri.length()
                - rawODataPath.length());

        odRequest.setRawQueryPath(httpRequest.getQueryString());
        odRequest.setRawRequestUri(rawRequestUri
                + (httpRequest.getQueryString() == null ? "" : "?"
                + httpRequest.getQueryString()));

        odRequest.setRawODataPath(rawODataPath);
        odRequest.setRawBaseUri(rawBaseUri);
        odRequest.setRawServiceResolutionUri(rawServiceResolutionUri);
    }

    /**
     * Extract headers.
     *
     * @param odRequest
     *            the od request
     * @param req
     *            the req
     */
    private void extractHeaders(final ODataRequest odRequest,
                                final HttpServletRequest req) {
        for (Enumeration<?> headerNames = req.getHeaderNames(); headerNames
                .hasMoreElements();) {
            String headerName = (String) headerNames.nextElement();
            List<String> headerValues = new ArrayList<String>();
            for (Enumeration<?> headers = req.getHeaders(headerName); headers
                    .hasMoreElements();) {
                String value = (String) headers.nextElement();
                headerValues.add(value);
            }
            odRequest.addHeader(headerName, headerValues);
        }
    }

    /**
     * Extract method.
     *
     * @param odRequest
     *            the od request
     * @param httpRequest
     *            the http request
     * @throws Exception
     *             the o data translated exception
     */
    private void extractMethod(final ODataRequest odRequest,
                               final HttpServletRequest httpRequest)
            throws Exception {
        try {
            HttpMethod httpRequestMethod = HttpMethod.valueOf(httpRequest
                    .getMethod());

            if (httpRequestMethod == HttpMethod.POST) {
                String xHttpMethod = httpRequest
                        .getHeader(HttpHeader.X_HTTP_METHOD);
                String xHttpMethodOverride = httpRequest
                        .getHeader(HttpHeader.X_HTTP_METHOD_OVERRIDE);

                if (xHttpMethod == null && xHttpMethodOverride == null) {
                    odRequest.setMethod(httpRequestMethod);
                } else if (xHttpMethod == null) {
                    odRequest
                            .setMethod(HttpMethod.valueOf(xHttpMethodOverride));
                } else if (xHttpMethodOverride == null) {
                    odRequest.setMethod(HttpMethod.valueOf(xHttpMethod));
                } else {
                    if (!xHttpMethod.equalsIgnoreCase(xHttpMethodOverride)) {
                        throw new ODataHandlerException(
                                "Ambiguous X-HTTP-Methods",
                                ODataHandlerException.MessageKeys.AMBIGUOUS_XHTTP_METHOD,
                                xHttpMethod, xHttpMethodOverride);
                    }
                    odRequest.setMethod(HttpMethod.valueOf(xHttpMethod));
                }
            } else {
                odRequest.setMethod(httpRequestMethod);
            }
        } catch (IllegalArgumentException e) {
            throw new ODataHandlerException("Invalid HTTP method"
                    + httpRequest.getMethod(),
                    ODataHandlerException.MessageKeys.INVALID_HTTP_METHOD,
                    httpRequest.getMethod());
        }
    }

}