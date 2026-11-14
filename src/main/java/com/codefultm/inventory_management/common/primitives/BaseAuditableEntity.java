package com.codefultm.inventory_management.common.primitives;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entity base class — all entities that need audit fields should extend this.
 * <p>
 * Spring Data JPA automatically populates {@code createdAt} and {@code updatedAt}
 * via {@link AuditingEntityListener}. The {@code createdBy} and {@code updatedBy}
 * fields are set manually (usually from the authenticated user).
 * </p>
 */
@MappedSuperclass  // Tells JPA: "this is not a table, but its fields are inherited by subclasses"
@EntityListeners(AuditingEntityListener.class)  // Enables automatic field population
public abstract class BaseAuditableEntity {

    // Automatically set by Spring Data when entity is first persisted
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Automatically updated by Spring Data on every entity save
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Set manually — stores the username of the user who created the entity
    @Column(name = "created_by", length = 100)
    private String createdBy;

    // Set manually — stores the username of the user who last updated the entity
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
