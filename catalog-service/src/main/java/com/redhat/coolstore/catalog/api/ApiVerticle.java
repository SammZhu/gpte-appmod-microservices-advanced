package com.redhat.coolstore.catalog.api;

import java.util.List;

import com.redhat.coolstore.catalog.model.Product;
import com.redhat.coolstore.catalog.verticle.service.CatalogService;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.vertx.ext.web.TracingHandler;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class ApiVerticle extends AbstractVerticle {

    private CatalogService catalogService;
    private JWTAuth jwtAuth;
    private CircuitBreaker circuitBreaker;
    private Tracer tracer;
    

    public ApiVerticle(CatalogService catalogService,JWTAuth jwtAuth) {
        this.catalogService = catalogService;
        this.jwtAuth = jwtAuth;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
    	
        tracer = GlobalTracer.get();
        Router router = Router.router(vertx);
        TracingHandler handler = new TracingHandler(tracer);
        router.route().order(-1).handler(handler).failureHandler(handler);
    	
    	AuthHandler authHandler = JWTAuthHandler.create(jwtAuth).addAuthority("coolstore");
        //Router router = Router.router(vertx);
        router.route().pathRegex("/product.*").handler(authHandler);
        router.get("/products").handler(this::getProducts);
        router.get("/product/:itemId").handler(this::getProduct);
        router.route("/product").handler(BodyHandler.create());
        router.post("/product").handler(this::addProduct);

        //Health Checks
        router.get("/health/readiness").handler(rc -> rc.response().end("OK"));
        HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx)
                .register("health", f -> health(f));
        router.get("/health/liveness").handler(healthCheckHandler);

    	circuitBreaker = CircuitBreaker.create("product-circuit-breaker", vertx,
                new CircuitBreakerOptions()
                    .setMaxFailures(3) // number of failure before opening the circuit
                    .setTimeout(1000) // consider a failure if the operation does not succeed in time
                    .setFallbackOnFailure(true) // do we call the fallback on failure
                    .setResetTimeout(5000) // time spent in open state before attempting to re-try
                );
    	
        vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(config().getInteger("catalog.http.port", 8080), result -> {
                if (result.succeeded()) {
                    startFuture.complete();
                } else {
                    startFuture.fail(result.cause());
                }
            });
    }

/*    private void getProducts(RoutingContext rc) {
        catalogService.getProducts(ar -> {
            if (ar.succeeded()) {
                List<Product> products = ar.result();
                JsonArray json = new JsonArray();
                products.stream()
                    .map(p -> p.toJson())
                    .forEach(p -> json.add(p));
                rc.response()
                    .putHeader("Content-type", "application/json")
                    .end(json.encodePrettily());
            } else {
                rc.fail(ar.cause());
            }
        });
    }*/
    
    private void getProducts(RoutingContext rc) {
        Span span = tracer.buildSpan("getProducts")
                .asChildOf(TracingHandler.serverSpanContext(rc))
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .startManual();
    	
    	JsonArray json = new JsonArray();
    	circuitBreaker.<JsonObject>execute(future -> catalogService.getProducts(ar -> {
    		span.finish();
            if (ar.succeeded()) {
                List<Product> products = ar.result();
                products.stream()
                    .map(p -> p.toJson())
                    .forEach(p -> json.add(p));
                future.complete();
            }
        })).setHandler(ar -> {
        	if (ar.succeeded()) {
        		rc.response()
        		.putHeader("Content-type", "application/json")
                .end(json.encodePrettily());
        	}else {
        		rc.fail(503);
            }
        });
    }

/*    private void getProduct(RoutingContext rc) {
        String itemId = rc.request().getParam("itemid");
        catalogService.getProduct(itemId, ar -> {
            if (ar.succeeded()) {
                Product product = ar.result();
                JsonObject json;
                if (product != null) {
                    json = product.toJson();
                    rc.response()
                        .putHeader("Content-type", "application/json")
                        .end(json.encodePrettily());
                } else {
                    rc.fail(404);
                }
            } else {
                rc.fail(ar.cause());
            }
        });
    }*/
    
    private void getProduct(RoutingContext rc) {
        Span span = tracer.buildSpan("getProduct")
                .asChildOf(TracingHandler.serverSpanContext(rc))
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .startManual();
    	
        String itemId = rc.request().getParam("itemid");
        circuitBreaker.<JsonObject>execute(future -> catalogService.getProduct(itemId, ar -> {
        	span.finish();
            if (ar.succeeded()) {
                Product product = ar.result();
                JsonObject json = null;
                if (product != null) {
                    json = product.toJson();
                }
                future.complete(json);
            }
        })).setHandler(ar -> {
            if (ar.succeeded()) {
                if (ar.result() != null) {
                    rc.response()
                            .putHeader("Content-type", "application/json")
                            .end(ar.result().encodePrettily());
                } else {
                    rc.response().setStatusCode(404).end();
                }
            } else {
                rc.fail(503);
            }
        });
    }

    private void addProduct(RoutingContext rc) {
        Span span = tracer.buildSpan("addProduct")
                .asChildOf(TracingHandler.serverSpanContext(rc))
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .startManual();
    	
        JsonObject json = rc.getBodyAsJson();
        catalogService.addProduct(new Product(json), ar -> {
        	span.finish();
            if (ar.succeeded()) {
                rc.response().setStatusCode(201).end();
            } else {
                rc.fail(ar.cause());
            }
        });
    }

    private void health(Future<Status> future) {
        catalogService.ping(ar -> {
            if (ar.succeeded()) {
                // HealthCheckHandler has a timeout of 1000s. If timeout is exceeded, the future will be failed
                if (!future.isComplete()) {
                    future.complete(Status.OK());
                }
            } else {
                if (!future.isComplete()) {
                    future.complete(Status.KO());
                }
            }
        });
    }

}
