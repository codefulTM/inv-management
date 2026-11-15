package com.codefultm.inventory_management.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    private final String entityType;
    private final Long entityId;

    public NotFoundException(String entityType, Long entityId) {
        super(entityType + " not found with id " + entityId);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    /**
     * Message-only variant, for lookups that are not keyed by id
     * (for example "no product with SKU 'ABC'").
     */
    public NotFoundException(String message) {
        super(message);
        this.entityType = null;
        this.entityId = null;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }
}
