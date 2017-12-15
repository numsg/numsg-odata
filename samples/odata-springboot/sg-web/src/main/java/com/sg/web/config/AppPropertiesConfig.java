package com.sg.web.config;

import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Created by numsg on 2017/3/6.
 */
@Configuration
public class AppPropertiesConfig {

    /**
     * Property sources placeholder configurer property sources placeholder configurer.
     *
     * @return the property sources placeholder configurer
     */
    @Bean
    public PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer p = new PropertySourcesPlaceholderConfigurer();
        p.setLocations(getCustomResources());
        p.setIgnoreUnresolvablePlaceholders(true);
        return p;
    }

    /**
     * Properties factory bean configurer properties factory bean.
     *
     * @return the properties factory bean
     */
    @Bean
    public PropertiesFactoryBean propertiesFactoryBeanConfigurer() {
        PropertiesFactoryBean p = new PropertiesFactoryBean();
        p.setLocations(getCustomResources());
        return p;
    }
    private  Resource[] getCustomResources() {
        return new ClassPathResource[]{
                new ClassPathResource("app.properties"),
        };
    }
}

