package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Data
public class MvtStkDto {

    private Long id;
    private Instant dateMvt;
    private BigDecimal quantite;
    private ArticleDto article;
    private TypeMvtStk typeMvt;
    private MotifMvtStk motif;
    private Long idEntreprise;
    /**
     * A la saisie seulement : la quantite est alors comptee dans ce conditionnement — deux cartons
     * casses — et le mouvement enregistre est converti en unites de base.
     */
    private ConditionnementDto conditionnement;
    /**
     * Le site dont le stock bouge. A la saisie, facultatif : le site actif. Sinon, il doit etre
     * l'un des sites de l'appelant.
     */
    private Long idSite;
    /** En lecture : le nom du site, pour l'historique d'un article. */
    private String nomSite;
    /**
     * Le lot. A une entree d'un article suivi : `idLot` d'un lot existant, ou son numero et sa date
     * pour en creer un. A une sortie : facultatif — sans lui, le lot qui perime le premier sort.
     */
    private Long idLot;
    private String numeroLot;
    private java.time.LocalDate datePeremption;
    /** Le document qui fait bouger le stock, pour retrouver les lots d'une vente ou d'un transfert. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long idVente;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long idTransfert;
    /** En lecture : ce qu'il faut savoir — un lot DLUO depasse est sorti. */
    private java.util.List<String> avertissements;

    public static MvtStkDto fromEntity(MvtStk mvtStk) {
        if (mvtStk == null) {
            return null;
        }
        return MvtStkDto.builder()
                .id(mvtStk.getId())
                .dateMvt(mvtStk.getDateMvt())
                .quantite(mvtStk.getQuantite())
                .article(ArticleDto.fromEntity(mvtStk.getArticles()))
                .typeMvt(mvtStk.getTypMvt())
                .motif(mvtStk.getMotif())
                .idEntreprise(mvtStk.getIdEntreprise())
                .idSite(mvtStk.getSite() == null ? null : mvtStk.getSite().getId())
                .nomSite(mvtStk.getSite() == null ? null : mvtStk.getSite().getNom())
                .idLot(mvtStk.getLot() == null ? null : mvtStk.getLot().getId())
                .numeroLot(mvtStk.getLot() == null ? null : mvtStk.getLot().getNumero())
                .datePeremption(mvtStk.getLot() == null ? null : mvtStk.getLot().getDatePeremption())
                .build();
    }

    public static MvtStk toEntity(MvtStkDto dto) {
        if (dto == null) {
            return null;
        }
        MvtStk mvtStk = new MvtStk();
        mvtStk.setId(dto.getId());
        mvtStk.setDateMvt(dto.getDateMvt());
        mvtStk.setQuantite(dto.getQuantite());
        mvtStk.setArticles(ArticleDto.toEntity(dto.getArticle()));
        mvtStk.setTypMvt(dto.getTypeMvt());
        mvtStk.setMotif(dto.getMotif());
        mvtStk.setIdEntreprise(dto.getIdEntreprise());
        return mvtStk;
    }
}
