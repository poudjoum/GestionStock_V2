package com.jumpy.tech.gestionstock.gestiondestock.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=true)
@Entity
public class MvtStk extends  AbstractEntity{
    @Column(name="dateMvt")
    private Instant dateMvt;
    @Column(name="Quantite")
    private BigDecimal quantite;

    @ManyToOne
    @JoinColumn(name="idArticle")
    private Article articles;
    @Column(name="typeMvt")
    private TypeMvtStk typMvt;
    // En clair : un motif mappe en rang se reinterpreterait si l'on intercalait une valeur, et
    // tout l'historique changerait de sens en silence.
    @Enumerated(EnumType.STRING)
    @Column(name="motif", length = 40)
    private MotifMvtStk motif;
    // Long comme partout ailleurs : c'etait un Integer, seule table a s'en ecarter, et un
    // cloisonnement qui compare des identifiants ne peut pas vivre avec deux types.
    @Column(name="idEntreprise")
    private Long idEntreprise;
}
