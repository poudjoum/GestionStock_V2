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
                .build();
    }
    public static Vente toEntity(VenteDto dto) {

        if(dto==null) {
            // TODO Auto-generated method stub
            return null;
        }

        Vente ven=new Vente();
        ven.setId(dto.getId());
        ven.setCode(dto.getCode());
        ven.setDatevente(dto.getDatevente());
        ven.setCommentaires(dto.getCommentaires());


        return ven;
    }
}
