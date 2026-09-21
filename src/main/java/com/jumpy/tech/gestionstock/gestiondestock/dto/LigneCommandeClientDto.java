package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeClient;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
@Data
@Builder
public class LigneCommandeClientDto {
    private Long id;
    private ArticleDto article;
    @JsonIgnore
    private CommandeClientDto cmndeClient;
    private BigDecimal quantite;
    /** Ce qui est deja parti. */
    private BigDecimal quantiteLivree;
    /**
     * Ce qui reste du : quantite commandee moins quantite servie.
     *
     * La quantite livree se portait sur la ligne depuis la livraison partielle, mais ce DTO ne la
     * remontait pas : le reliquat d'une commande client etait invisible de l'exterieur, et une
     * commande cloturee ne disait pas ce qui n'avait pas ete honore.
     */
    private BigDecimal resteAServir;
    private BigDecimal prixUnitaire;

    public static LigneCommandeClientDto fromEntity(LigneCmndeClient ligneCmndeClient){
        if(ligneCmndeClient==null){
            return null;
        }
        return LigneCommandeClientDto.builder()
                        .id(ligneCmndeClient.getId())
                                .article(ArticleDto.fromEntity(ligneCmndeClient.getArticles()))
                                        .cmndeClient(CommandeClientDto.fromEntity(ligneCmndeClient.getCommandeClient()))
                                                .quantite(ligneCmndeClient.getQuantite())
                                                        .quantiteLivree(ligneCmndeClient.getQuantiteLivree())
                                                                .resteAServir(reste(ligneCmndeClient.getQuantite(), ligneCmndeClient.getQuantiteLivree()))
                                                        .prixUnitaire(ligneCmndeClient.getPrixUnitaire())
                                                                .build();
    }

    /** Jamais negatif : une ligne ne peut pas « rester a servir » moins que rien. */
    private static BigDecimal reste(BigDecimal commandee, BigDecimal servie) {
        if (commandee == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal deja = servie == null ? BigDecimal.ZERO : servie;
        return commandee.subtract(deja).max(BigDecimal.ZERO);
    }
    public static LigneCmndeClient toEntity(LigneCommandeClientDto dto){
        if(dto==null){
            return null;
        }
        LigneCmndeClient lcc=new LigneCmndeClient();
        lcc.setId(dto.getId());
        lcc.setArticles(ArticleDto.toEntity(dto.getArticle()));
        lcc.setPrixUnitaire(dto.getPrixUnitaire());
        lcc.setQuantite(dto.getQuantite());
        return lcc;

    }
}
