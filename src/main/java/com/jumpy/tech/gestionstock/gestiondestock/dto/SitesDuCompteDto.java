package com.jumpy.tech.gestionstock.gestiondestock.dto;

import java.util.List;

/**
 * Les sites ou travaille un collaborateur, et celui ou il arrive. Une liste vide le renvoie au
 * site principal.
 */
public record SitesDuCompteDto(List<Long> idsSites, Long idSiteDefaut) {
}
