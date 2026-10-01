package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InscriptionClientDto {
    private String telephone;
    private String nom;
    private String prenom;
    private String motDePasse;
}
