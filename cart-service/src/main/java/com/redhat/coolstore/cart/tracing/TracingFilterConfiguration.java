package com.redhat.coolstore.cart.tracing;

import java.util.Collections;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentracing.Tracer;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;

@Configuration
public class TracingFilterConfiguration {

    @Value("${opentracing.servlet.skipPattern}")
    private String skipPattern;

    @Bean
    public FilterRegistrationBean tracingFilter(Tracer tracer) {

        TracingFilter tracingFilter = new TracingFilter(tracer,
                Collections.singletonList(ServletFilterSpanDecorator.STANDARD_TAGS), Pattern.compile(skipPattern));

        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(tracingFilter);
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(Integer.MIN_VALUE);
        filterRegistrationBean.setAsyncSupported(true);

        return filterRegistrationBean;
    }
}
