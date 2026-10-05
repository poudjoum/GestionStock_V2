package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatTransfert;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Transfert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Un transfert entre deux sites, et ses lignes. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransfertDto {
    private Long id;
    private String reference;
    /** A la creation, facultatif : le site actif. */
    private Long idSiteSource;
    private String nomSiteSource;
    private Long idSiteDestination;
    private String nomSiteDestination;
    private EtatTransfert etat;
    private String commentaire;
    private Instant dateCreation;
    private Instant dateExpedition;
    private Instant dateReception;
    private List<LigneTransfertDto> lignes;

    public static TransfertDto fromEntity(Transfert t, List<LigneTransfertDto> lignes) {
        if (t == null) {
            return null;
        }
        return TransfertDto.builder()
                .id(t.getId())
                .reference(t.getReference())
                .idSiteSource(t.getSource().getId())
                .nomSiteSource(t.getSource().getNom())
                .idSiteDestination(t.getDestination().getId())
                .nomSiteDestination(t.getDestination().getNom())
                .etat(t.getEtat())
                .commentaire(t.getCommentaire())
                .dateCreation(t.getCreationDate())
                .dateExpedition(t.getDateExpedition())
                .dateReception(t.getDateReception())
                .lignes(lignes)
                .build();
    }
}
