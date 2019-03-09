package com.redhat.coolstore.inventory.rest;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.wildfly.swarm.spi.runtime.annotations.ConfigurationValue;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.redhat.coolstore.inventory.model.Inventory;
import com.redhat.coolstore.inventory.service.InventoryService;
import com.redhat.coolstore.inventory.service.StoreStatusService;

import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

@Path("/inventory")
@RequestScoped
public class InventoryResource {

    @Inject
    private InventoryService inventoryService;

    @Inject
    private StoreStatusService storeStatusService;

    @Inject
    @ConfigurationValue("hystrix.inventory.circuitBreaker.requestVolumeThreshold")
    private int hystrixCircuitBreakerRequestVolumeThreshold;
    
    @Inject
    @ConfigurationValue("hystrix.inventory.groupKey")
    private String hystrixGroupKey;
    
    
    class GetInventoryCommand extends HystrixCommand<Inventory> {

        private String itemId;
        
        public GetInventoryCommand(String itemId) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(hystrixGroupKey))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().
                            withCircuitBreakerRequestVolumeThreshold(hystrixCircuitBreakerRequestVolumeThreshold)));
            this.itemId = itemId;
        }

        @Override
        protected Inventory run() throws Exception {
        	return inventoryService.getInventory(itemId);
        }
    }
    
    class GetStatusCommand extends HystrixCommand<String> {

        private String itemId;
        
        public GetStatusCommand(String itemId) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(hystrixGroupKey))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().
                            withCircuitBreakerRequestVolumeThreshold(hystrixCircuitBreakerRequestVolumeThreshold)));
            this.itemId = itemId;
        }

        @Override
        protected String run() throws Exception {
        	return storeStatusService.storeStatus(itemId);
        }
    }
    
    @GET
    @Path("/{itemId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Inventory getInventory(@PathParam("itemId") String itemId, @DefaultValue("false") @QueryParam("storeStatus") boolean storeStatus) {
    	try (Scope span = buildChildSpan()) {
	    	GetInventoryCommand command = new GetInventoryCommand(itemId);
	    	
	        //Inventory inventory = inventoryService.getInventory(itemId);
	    	Inventory inventory = null;
	    	try {
				inventory = command.run();
			} catch (Exception e) {
				if(e instanceof HystrixRuntimeException) throw new WebApplicationException(503);
				else throw new WebApplicationException(503);
			}
	        if (inventory == null) {
	            throw new NotFoundException();
	        } else {
	            if (storeStatus) {
	            	GetStatusCommand statusComm = new GetStatusCommand(inventory.getLocation());
	            	String status = null;
					try {
						status = statusComm.run();
					} catch (Exception e) {
						if(e instanceof HystrixRuntimeException) status = "N/A";
					}
	                //String status = storeStatusService.storeStatus(inventory.getLocation());
	                inventory.setLocation(inventory.getLocation() + " [" + status + "]");
	            }
	            return inventory;
	        }
    	}
    }
    
    private Scope buildChildSpan() {
        Tracer tracer = GlobalTracer.get();
        Scope activeScope = tracer.scopeManager().active();
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan("getInventory")
                .withTag(Tags.SPAN_KIND.getKey(), "database");
        if (activeScope != null) {
            spanBuilder.asChildOf(activeScope.span());
        }
        return spanBuilder.startActive(true);
    }
}
