package com.redhat.coolstore.catalog.verticle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.coolstore.catalog.api.ApiVerticle;
import com.redhat.coolstore.catalog.verticle.service.CatalogService;
import com.redhat.coolstore.catalog.verticle.service.CatalogVerticle;

import io.jaegertracing.Configuration;
import io.opentracing.util.GlobalTracer;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;

public class MainVerticle extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        ConfigStoreOptions jsonConfigStore = new ConfigStoreOptions().setType("json");
        ConfigStoreOptions appStore = new ConfigStoreOptions()
            .setType("configmap")
            .setFormat("yaml")
            .setConfig(new JsonObject()
                .put("name", System.getenv("APP_CONFIGMAP_NAME"))
                .put("key", System.getenv("APP_CONFIGMAP_KEY")));

        ConfigRetrieverOptions options = new ConfigRetrieverOptions();
        if (System.getenv("KUBERNETES_NAMESPACE") != null) {
            //we're running in Kubernetes
            options.addStore(appStore);
        } else {
            //default to json based config
            jsonConfigStore.setConfig(config());
            options.addStore(jsonConfigStore);
        }

        ConfigRetriever.create(vertx, options)
            .getConfig(ar -> {
                if (ar.succeeded()) {
                    deployVerticles(ar.result(), startFuture);
                } else {
                    System.out.println("Failed to retrieve the configuration.");
                    startFuture.fail(ar.cause());
                }
            });
    }

    private void deployVerticles(JsonObject config, Future<Void> startFuture) {
    	initTracer(config);
    	
    	JsonObject jwtConfig = new JsonObject()
    	        .put("public-key", config.getString("rhsso-public-key"))
    	        .put("permissionsClaimKey", config.getString("rhsso-permissions-claim-key"))
    	        .put("issuer", config.getString("rhsso-issuer"));
    	JWTAuth jwtAuth = JWTAuth.create(vertx, jwtConfig);

        Future<String> apiVerticleFuture = Future.future();
        Future<String> catalogVerticleFuture = Future.future();

        CatalogService catalogService = CatalogService.createProxy(vertx);
        DeploymentOptions options = new DeploymentOptions();
        options.setConfig(config);
        vertx.deployVerticle(new CatalogVerticle(), options, catalogVerticleFuture.completer());
        vertx.deployVerticle(new ApiVerticle(catalogService, jwtAuth), options, apiVerticleFuture.completer());

        CompositeFuture.all(apiVerticleFuture, catalogVerticleFuture).setHandler(ar -> {
            if (ar.succeeded()) {
                System.out.println("Verticles deployed successfully.");
                startFuture.complete();
            } else {
                System.out.println("WARNINIG: Verticles NOT deployed successfully.");
                startFuture.fail(ar.cause());
            }
        });

    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        super.stop(stopFuture);
    }
    
    private void initTracer(JsonObject config) {
        String serviceName = config.getString("service-name");
        if (serviceName == null || serviceName.isEmpty()) {
            log.info("No Service Name set. Skipping initialization of the Jaeger Tracer.");
            return;
        }

        Configuration configuration = new Configuration(serviceName)
                .withSampler(new Configuration.SamplerConfiguration()
                        .withType(config.getString("sampler-type"))
                        .withParam(getPropertyAsNumber(config, "sampler-param"))
                        .withManagerHostPort(config.getString("sampler-manager-host-port")))
                .withReporter(new Configuration.ReporterConfiguration()
                        .withLogSpans(config.getBoolean("reporter-log-spans"))
                        .withFlushInterval(config.getInteger("reporter-flush-interval"))
                        .withMaxQueueSize(config.getInteger("reporter-flush-interval"))
                        .withSender(new Configuration.SenderConfiguration()
                                .withAgentHost(config.getString("agent-host"))
                                .withAgentPort(config.getInteger("agent-port"))));
        GlobalTracer.register(configuration.getTracer());
    }

    private Number getPropertyAsNumber(JsonObject json, String key) {
        Object o  = json.getValue(key);
        if (o instanceof Number) {
            return (Number) o;
        }
        return null;
    }

}
