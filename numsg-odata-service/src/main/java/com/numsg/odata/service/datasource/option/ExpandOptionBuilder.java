package com.numsg.odata.service.datasource.option;

import com.numsg.odata.service.datasource.NumsgDataProvider;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;

import java.util.List;

/**
 * Created by gaoqiang on 2017/4/27.
 */

/**
 * Created by gaoqiang on 2017/4/27.
 */
public class ExpandOptionBuilder {

    public static void handlerExpandOption(ExpandOption expandOption, Entity responseEntity, EdmEntitySet responseEdmEntitySet, NumsgDataProvider dataProvider) {
        if (expandOption != null) {
            // retrieve the EdmNavigationProperty from the expand expression
            // Note: in our example, we have only one NavigationProperty, so we can directly access it
            EdmNavigationProperty edmNavigationProperty = null;
//            ExpandItem expandItem = expandOption.getExpandItems().get(0);
            for (ExpandItem expandItem : expandOption.getExpandItems()) {
                if (expandItem.isStar()) {
                    List<EdmNavigationPropertyBinding> bindings = responseEdmEntitySet.getNavigationPropertyBindings();
                    // we know that there are navigation bindings
                    // however normally in this case a check if navigation bindings exists is done
                    if (!bindings.isEmpty()) {
                        // can in our case only be 'Category' or 'Products', so we can take the first
                        EdmNavigationPropertyBinding binding = bindings.get(0);
                        EdmElement property = responseEdmEntitySet.getEntityType().getProperty(binding.getPath());
                        // we don't need to handle error cases, as it is done in the Olingo library
                        if (property instanceof EdmNavigationProperty) {
                            edmNavigationProperty = (EdmNavigationProperty) property;
                        }
                    }
                } else {
                    // can be 'Category' or 'Products', no path supported
                    UriResource expandUriResource = expandItem.getResourcePath().getUriResourceParts().get(0);
                    // we don't need to handle error cases, as it is done in the Olingo library
                    if (expandUriResource instanceof UriResourceNavigation) {
                        edmNavigationProperty = ((UriResourceNavigation) expandUriResource).getProperty();
                    }
                }

                // can be 'Category' or 'Products', no path supported
                // we don't need to handle error cases, as it is done in the Olingo library
                if (edmNavigationProperty != null) {
                    EdmEntityType expandEdmEntityType = edmNavigationProperty.getType();
                    String navPropName = edmNavigationProperty.getName();

                    // build the inline data
                    Link link = new Link();
                    link.setTitle(navPropName);
                    link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
                    link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);

                    if (edmNavigationProperty.isCollection()) { // in case of Categories(1)/$expand=Products
                        // fetch the data for the $expand (to-many navigation) from backend
                        // here we get the data for the expand
                        EntityCollection expandEntityCollection =
                                dataProvider.getRelatedEntityCollection(responseEntity, expandEdmEntityType);
                        link.setInlineEntitySet(expandEntityCollection);
                        link.setHref(expandEntityCollection.getId().toASCIIString());
                    } else {  // in case of Products(1)?$expand=Category
                        // fetch the data for the $expand (to-one navigation) from backend
                        // here we get the data for the expand
                        Entity expandEntity = dataProvider.getNavigationEntity(responseEntity, expandEdmEntityType);
                        link.setInlineEntity(expandEntity);
                        if (expandEntity != null)
                            link.setHref(expandEntity.getId().toASCIIString());
                    }

                    // set the link - containing the expanded data - to the current entity
                    responseEntity.getNavigationLinks().add(link);
                }
            }
        }
    }
}