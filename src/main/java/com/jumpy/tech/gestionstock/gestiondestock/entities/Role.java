package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name="Role")
public class Role extends AbstractEntity{
    @Enumerated(EnumType.STRING)
    private ERole roleName;

    @Column(name="idEntreprise")
    private Long idEntreprise;
}
