package com.crossborderpayment.model;

import java.io.Serializable;
import java.util.Objects;

import org.hibernate.proxy.HibernateProxy;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class BaseModel implements Serializable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public BaseModel(){
    }

    public BaseModel(Long id){
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // Equality is based on the database id and the real entity class, so that a Hibernate proxy
    // (lazy reference) and the loaded entity with the same id are equal.
    // Entities without an id (not saved yet) are only equal to themselves.
    // final: a proxy must not intercept these methods, otherwise it loads the entity (and fails outside a session).
    @Override
    public final boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (effectiveClass(this) != effectiveClass(obj))
            return false;
        BaseModel other = (BaseModel) obj;
        // Use getId(): on a proxy it returns the id without loading the entity
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    // Constant per entity class so the value does not change when the id is assigned on save
    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object obj) {
        return obj instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : obj.getClass();
    }
}
