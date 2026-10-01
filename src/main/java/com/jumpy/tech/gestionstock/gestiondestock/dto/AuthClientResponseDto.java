package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthClientResponseDto {
    private String jeton;
    private Long id;
    private String telephone;
    private String nom;
    private String prenom;
    private int totalPoints;
}
