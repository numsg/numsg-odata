package com.sg.web.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by numsg on 2017/3/6.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories("com.sg.odata.service")
@PropertySource(value = {"classpath:app.properties"})
public class JPADataSourceConfig {
    @Autowired
    private Environment env;

    /**
     * 创建事务管理器
     * @return JpaTransactionManager类的实例.
     */
    @Bean
    public PlatformTransactionManager transactionManager() {
        return new JpaTransactionManager(entityManagerFactory());
    }

    /**
     * 创建EntityManagerFactory。
     * @return EntityManagerFactory的实例，该方法需要使用类路径下的persistence.properties文件。
     */
    @Bean
    public EntityManagerFactory entityManagerFactory(){
        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
                new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setDataSource(dataSourceBean());
        entityManagerFactoryBean.setPackagesToScan(env.getProperty("domainPackagesToScan"));
        entityManagerFactoryBean.setJpaVendorAdapter(jpaVendorAdapter());
        entityManagerFactoryBean.setJpaProperties(addJpaProperties());
        entityManagerFactoryBean.afterPropertiesSet();
        return entityManagerFactoryBean.getObject();
    }

    /**
     * 创建DataSource对象
     * @return DataSource的实例，该方法需要使用类路径下的persistence.properties文件。
     */
    @Bean
    public DataSource dataSourceBean(){
        String driverClassName = env.getProperty("jdbc.driverClassName");
        String url = env.getProperty("jdbc.url");
        String userName=env.getProperty("jdbc.username");
        String password=env.getProperty("jdbc.password");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(userName);
        dataSource.setPassword(password);

        return dataSource;
    }

    /**
     * 创建JpaVendorAdapter
     * @return HibernateJpaVendorAdapter的实例　
     */
    @Bean
    public JpaVendorAdapter jpaVendorAdapter(){
        return  new HibernateJpaVendorAdapter();
    }

    private Properties addJpaProperties(){
        Map<String,String> jpaPropertiesMap = new HashMap<>();
        jpaPropertiesMap.put("hibernate.dialect",env.getProperty("hibernate.dialect"));
        jpaPropertiesMap.put("hibernate.show_sql",env.getProperty("hibernate.show_sql"));
        jpaPropertiesMap.put("hibernate.format_sql",env.getProperty("hibernate.format_sql"));
        jpaPropertiesMap.put("hibernate.hbm2ddl.auto",env.getProperty("hibernate.hbm2ddl.auto"));

        Properties jpaProperties = new Properties();
        jpaProperties.putAll(jpaPropertiesMap);
        return jpaProperties;
    }

//    @Bean
//    public List<CsdlEntitySet> csdlEntitySets(){
//        return Arrays.asList(
//                new CsdlEntitySet().setName("Cars").setType(new FullQualifiedName("com.numsg.odata.service.model","Car")),
//                new CsdlEntitySet().setName("Manufacturers").setType(new FullQualifiedName("com.numsg.odata.service.model","Manufacturer"))
//        );
//    }
}
