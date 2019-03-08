package com.redhat.coolstore.inventory.service;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.coolstore.inventory.model.Inventory;

@ApplicationScoped
public class InventoryService {
	//Logger logger = LoggerFactory.getLogger(InventoryService.class);

    @PersistenceContext(unitName = "primary")
    private EntityManager em;

    public Inventory getInventory(String itemId) {
        Inventory inventory = em.find(Inventory.class, itemId);
        return inventory;
    }

}
