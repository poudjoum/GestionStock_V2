package com.jumpy.tech.gestionstock.gestiondestock.rapport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** Un resume deja parti : un serveur qui redemarre ne le renvoie pas. */
@Data
@NoArgsConstructor
@Entity
@Table(name = "resume_envoye")
public class ResumeEnvoye {

    public enum Type { QUOTIDIEN, HEBDO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_entreprise", nullable = false)
    private Long idEntreprise;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 12)
    private Type type;

    /** Le premier jour couvert : la veille, ou le lundi de la semaine ecoulee. */
    @Column(name = "debut", nullable = false)
    private LocalDate debut;

    @Column(name = "envoye_le", nullable = false)
    private Instant envoyeLe;
}
