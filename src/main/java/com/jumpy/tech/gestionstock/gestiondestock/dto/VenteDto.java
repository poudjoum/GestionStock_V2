package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Builder
@Data
public class VenteDto {
    private Long id;
    private String code;
    private Instant datevente;
    private String Commentaires;
    private boolean annulee;
    /** A qui l'on vend, s'il est connu. Une vente de comptoir anonyme n'en a pas. */
    private ClientDto client;
    /** L'entreprise qui vend : c'est elle qui porte le regime de TVA applique a la facture. */
    private Long idEntreprise;
    /** Nul pour une vente au comptoir ; renseigne quand la vente sert une commande client. */
    private Long idCommandeClient;
    private List<LigneVenteDto> ligneVente;
    public static VenteDto fromEntity(Vente vente) {
        if(vente==null) {
            // TODO Auto-generated method stub
            return null;
        }

        return VenteDto.builder()
                .id(vente.getId())
                .code(vente.getCode())
                .datevente(vente.getDatevente())
                .Commentaires(vente.getCommentaires())
                .annulee(vente.isAnnulee())
                .client(ClientDto.fromEntity(vente.getClient()))
                .idEntreprise(vente.getIdEntreprise())
                .idCommandeClient(vente.getCommandeClient() == null ? null : vente.getCommandeClient().getId())
                .build();
    }
    public static Vente toEntity(VenteDto dto) {

        if(dto==null) {
            return null;
        }

        Vente ven=new Vente();
        ven.setId(dto.getId());
        ven.setCode(dto.getCode());
        ven.setDatevente(dto.getDatevente());
        ven.setCommentaires(dto.getCommentaires());
        ven.setIdEntreprise(dto.getIdEntreprise());

        return ven;
    }
}
