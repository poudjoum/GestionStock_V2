package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
@EqualsAndHashCode


public class Adresse {
    @Column(name="adresse1")
    private String adresse1;
    @Column(name="adresse2")
    private String adresse2;
    @Column(name="ville")
    private String ville;
    @Column(name="code_postale")
    private String codePostal;
    @Column(name="pays")
    private String pays;
}
