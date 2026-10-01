package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfilClientFideliteDto {
    private Long id;
    private String telephone;
    private String nom;
    private String prenom;
    private int totalPoints;
    private List<SoldePointsMagasinDto> soldesParMagasin;
}
