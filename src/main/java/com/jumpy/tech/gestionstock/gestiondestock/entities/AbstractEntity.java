package com.jumpy.tech.gestionstock.gestiondestock.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)

public class AbstractEntity implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="creationDate",nullable = false,updatable = false)

    private Instant creationDate;
    @Column(name = "lastModifiedDate")
    private Instant lastUpdate;

    @PrePersist
    void prePersist(){
        creationDate= Instant.now();
    }
    @PreUpdate
    void preUpdate(){
        lastUpdate=Instant.now();
    }
}
