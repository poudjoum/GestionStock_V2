package com.jumpy.tech.gestionstock.gestiondestock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Les sites ou le compte connecte peut travailler, et celui qui est actif : de quoi dessiner le
 * selecteur de site. Un seul site : le selecteur n'a pas lieu d'etre.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesSitesDto {
    private List<SiteDto> sites;
    private Long actif;
    /** Peut-il regarder le stock de tous les sites a la fois ? */
    private boolean tousLesSites;
}
