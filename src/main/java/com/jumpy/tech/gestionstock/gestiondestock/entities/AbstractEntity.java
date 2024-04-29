package com.jumpy.tech.gestionstock.gestiondestock.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDate;
@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)

public class AbstractEntity implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="creationDate")
    @JsonIgnore
    private LocalDate creationDate;
    @Column(name = "lastModifiedDate")
    private LocalDate lastUpdate;

    @PrePersist
    void prePersist(){
        creationDate= LocalDate.now();
    }
    @PreUpdate
    void preUpdate(){
        lastUpdate=LocalDate.now();
    }
}
