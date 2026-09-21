package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Facture;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutReglement;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Data
public class FactureDto {

    private Long id;
    private String numero;
    private Instant dateEmission;
    private Long idVente;
    private String codeVente;
    private BigDecimal totalHt;
    private BigDecimal totalTva;
    private BigDecimal totalTtc;
    private boolean annulee;
    /** Faux quand l'entreprise n'est pas assujettie : c'est ce qui explique une TVA a zero. */
    private boolean tvaApplicable;

    // Deduits de la somme des reglements a chaque lecture, jamais stockes : une facture marquee
    // reglee dont les encaissements ne suivent pas serait pire que pas d'information du tout.
    private BigDecimal montantRegle;
    private BigDecimal resteAPayer;
    private StatutReglement statutReglement;
    private Long idClient;
    private String nomClient;
    private Long idEntreprise;
    private List<LigneFactureDto> lignes;

    public static FactureDto fromEntity(Facture facture) {
        return fromEntity(facture, null);
    }

    /**
     * Les lignes sont passees a part plutot que lues depuis l'entite : une facture se consulte
     * souvent en liste, et charger les lignes de chacune pour les jeter aussitot coute cher.
     */
    public static FactureDto fromEntity(Facture facture, List<LigneFactureDto> lignes) {
        if (facture == null) {
            return null;
        }
        return FactureDto.builder()
                .id(facture.getId())
                .numero(facture.getNumero())
                .dateEmission(facture.getDateEmission())
                .idVente(facture.getVente() == null ? null : facture.getVente().getId())
                .codeVente(facture.getVente() == null ? null : facture.getVente().getCode())
                .totalHt(facture.getTotalHt())
                .totalTva(facture.getTotalTva())
                .totalTtc(facture.getTotalTtc())
                .annulee(facture.isAnnulee())
                .tvaApplicable(facture.isTvaApplicable())
                .idClient(facture.getClient() == null ? null : facture.getClient().getId())
                .nomClient(facture.getNomClient())
                .idEntreprise(facture.getIdEntreprise())
                .lignes(lignes)
                .build();
    }

    public static FactureDto avecLignes(Facture facture, List<com.jumpy.tech.gestionstock.gestiondestock.entities.LigneFacture> lignes) {
        return fromEntity(facture, lignes.stream()
                .map(LigneFactureDto::fromEntity)
                .collect(Collectors.toList()));
    }

    /**
     * Complete la facture de l'etat de son reglement.
     *
     * Une facture annulee n'est jamais « reglee » : elle ne doit plus rien, et l'afficher comme
     * impayee ferait croire a une creance qui n'existe pas.
     */
    public FactureDto avecReglement(BigDecimal montantRegle) {
        BigDecimal regle = montantRegle == null ? BigDecimal.ZERO : montantRegle;
        BigDecimal du = totalTtc == null ? BigDecimal.ZERO : totalTtc;
        BigDecimal reste = du.subtract(regle);

        this.montantRegle = regle;
        this.resteAPayer = reste.max(BigDecimal.ZERO);
        if (annulee) {
            this.statutReglement = null;
        } else if (reste.signum() <= 0) {
            this.statutReglement = StatutReglement.REGLEE;
        } else if (regle.signum() > 0) {
            this.statutReglement = StatutReglement.PARTIELLEMENT_REGLEE;
        } else {
            this.statutReglement = StatutReglement.IMPAYEE;
        }
        return this;
    }
}
