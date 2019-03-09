package com.redhat.coolstore.cart.tracing;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

@Configuration
public class TracerRegistration {

    @Autowired
    private Tracer tracer;

    @PostConstruct
    public void registerToGlobalTracer() {

        if (!GlobalTracer.isRegistered()) {
            GlobalTracer.register(tracer);
        }
    }
}