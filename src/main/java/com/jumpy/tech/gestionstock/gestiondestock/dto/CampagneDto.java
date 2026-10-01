package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Campagne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Une campagne de promotion, telle que le gerant la saisit et la relit. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampagneDto {

    public enum Statut { A_VENIR, EN_COURS, TERMINEE, ARRETEE }

    private Long id;
    private String titre;
    private String message;
    private String image;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    /** Calcule, en lecture seule. */
    private Statut statut;
    @Builder.Default
    private List<PromotionArticleDto> promotions = new ArrayList<>();

    public static CampagneDto de(Campagne campagne, LocalDate aujourdhui) {
        return CampagneDto.builder()
                .id(campagne.getId())
                .titre(campagne.getTitre())
                .message(campagne.getMessage())
                .image(campagne.getImage())
                .dateDebut(campagne.getDateDebut())
                .dateFin(campagne.getDateFin())
                .statut(statut(campagne, aujourdhui))
                .promotions(campagne.getPromotions().stream().map(PromotionArticleDto::de).toList())
                .build();
    }

    public static Statut statut(Campagne campagne, LocalDate aujourdhui) {
        if (campagne.isArretee()) {
            return Statut.ARRETEE;
        }
        if (aujourdhui.isBefore(campagne.getDateDebut())) {
            return Statut.A_VENIR;
        }
        return aujourdhui.isAfter(campagne.getDateFin()) ? Statut.TERMINEE : Statut.EN_COURS;
    }
}
