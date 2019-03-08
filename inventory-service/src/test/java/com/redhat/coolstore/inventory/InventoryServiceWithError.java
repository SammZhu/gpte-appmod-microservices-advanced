package com.redhat.coolstore.inventory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Specializes;
import javax.ws.rs.WebApplicationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.coolstore.inventory.model.Inventory;
import com.redhat.coolstore.inventory.service.InventoryService;

@ApplicationScoped
@Specializes
public class InventoryServiceWithError extends InventoryService {
	Logger logger = LoggerFactory.getLogger(InventoryServiceWithError.class);

    @Override
    public Inventory getInventory(String itemId) {
        if ("error".equalsIgnoreCase(itemId)) {
        	logger.error("simulate getInentory error");
            throw new RuntimeException();
        }
        if ("timeout".equalsIgnoreCase(itemId)) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
            }
        }
        return super.getInventory(itemId);
    }
}
