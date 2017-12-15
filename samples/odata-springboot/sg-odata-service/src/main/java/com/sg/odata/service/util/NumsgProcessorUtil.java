package com.sg.odata.service.util;


import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriInfoResource;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;

import java.util.List;
import java.util.Locale;

/**
 * Created by gaoqiang on 2017/4/27.
 */
public class NumsgProcessorUtil {

    public static EdmEntitySet getFirstEdmEntitySet(final UriInfoResource uriInfo) throws ODataApplicationException {
        final List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        /*
         * To get the entity set we have to interpret all URI segments
         */
        if (!(resourcePaths.get(0) instanceof UriResourceEntitySet)) {
            throw new ODataApplicationException("Invalid resource type for first segment.",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }

        /*
         * Here we should interpret the whole URI but in this example we do not support navigation so we throw an exception
         */

        for(UriResource uriResource :resourcePaths){
            if (uriResource instanceof UriResourceEntitySet) {

            }
        }
        final UriResourceEntitySet uriResource = (UriResourceEntitySet) resourcePaths.get(0);
        return uriResource.getEntitySet();
    }
}
