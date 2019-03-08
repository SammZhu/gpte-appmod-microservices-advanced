package com.redhat.coolstore.cart.service;

import org.keycloak.adapters.springsecurity.client.KeycloakClientRequestFactory;
import org.keycloak.adapters.springsecurity.client.KeycloakRestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.redhat.coolstore.cart.model.Product;

@Component
public class CatalogServiceImpl implements CatalogService {
	Logger logger = LoggerFactory.getLogger(CatalogServiceImpl.class);

    @Value("${catalog.service.url}")
    private String catalogServiceUrl;
    
    @Autowired
    private KeycloakClientRequestFactory keycloakClientRequestFactory;

    //@HystrixCommand(commandKey = "CatalogService", fallbackMethod = "getFallbackProduct")
    /*@HystrixCommand(commandKey = "CatalogService", groupKey = "testGroup", threadPoolKey = "testThreadKey",
    fallbackMethod = "getFallbackProduct", ignoreExceptions = {NullPointerException.class},
    threadPoolProperties = {
            @HystrixProperty(name = "coreSize", value = "30"),
            @HystrixProperty(name = "maxQueueSize", value = "101"),
            @HystrixProperty(name = "keepAliveTimeMinutes", value = "2"),
            @HystrixProperty(name = "queueSizeRejectionThreshold", value = "15"),
            @HystrixProperty(name = "metrics.rollingStats.numBuckets", value = "12"),
            @HystrixProperty(name = "metrics.rollingStats.timeInMilliseconds", value = "1440")
    }
    )*/
    @Override
    public Product getProduct(String itemId) {
        //RestTemplate restTemplate = new RestTemplate();
    	RestTemplate restTemplate = new KeycloakRestTemplate(keycloakClientRequestFactory);
    	return call(itemId, restTemplate);
        /*ResponseEntity<Product> entity;
        try {
        	logger.info("--------getProduct {}|{}",catalogServiceUrl,itemId);
            entity = restTemplate.getForEntity(catalogServiceUrl + "/product/" + itemId, Product.class);
            logger.info("==========getProduct {}",itemId);
            return entity.getBody();
        } catch (HttpClientErrorException e) {
            e.printStackTrace();
            if (e.getRawStatusCode() == 404) {
                return null;
            } else {
                throw e;
            }
        }*/
    }
    
    @HystrixCommand(commandKey = "CatalogService", fallbackMethod = "getFallbackProduct")
    public Product call(String itemId, RestTemplate restTemplate) {
    	ResponseEntity<Product> entity;
        try {
        	logger.info("--------getProduct {}|{}",catalogServiceUrl,itemId);
            entity = restTemplate.getForEntity(catalogServiceUrl + "/product/" + itemId, Product.class);
            logger.info("==========getProduct {}",itemId);
            return entity.getBody();
        } catch (HttpClientErrorException e) {
            e.printStackTrace();
            if (e.getRawStatusCode() == 404) {
                return null;
            } else {
                throw e;
            }
        }
    }
    
    public Product getFallbackProduct(String itemId){
    	logger.info("getFallbackProduct {}",itemId);
    	return null;
    }
}
