package com.sg.web;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Created by gaoqiang on 2017/7/19.
 */

@SpringBootApplication
@EnableWebMvc
@EnableAutoConfiguration
@EnableConfigurationProperties
@ComponentScan(basePackages = "com.sg.*")
public class OlingoApplication {
    public static void main(String[] args) {
        SpringApplication.run(OlingoApplication.class,args);
    }
}
