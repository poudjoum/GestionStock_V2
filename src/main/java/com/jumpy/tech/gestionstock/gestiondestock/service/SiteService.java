package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.MesSitesDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SiteDto;

import java.math.BigDecimal;
import java.util.List;

/** Les magasins et les entrepots d'une entreprise. */
public interface SiteService {

    /** Tous les sites de l'entreprise, fermes compris, le principal d'abord. */
    List<SiteDto> sites();

    /** Les sites ou le compte connecte travaille, et celui qui est actif. */
    MesSitesDto mesSites();

    SiteDto creer(SiteDto dto);

    SiteDto modifier(Long id, SiteDto dto);

    /**
     * Ferme un site. Refuse pour le site principal, et tant qu'il reste du stock : il se
     * transfere d'abord, sans quoi il sortirait des comptes sans etre sorti du depot.
     */
    SiteDto fermer(Long id);

    /** Cree le site principal d'une entreprise qui vient de naitre. */
    void creerSitePrincipal(Long idEntreprise);

    /** Le seuil d'alerte de l'article dans ce site ; nul, celui de l'article s'applique. */
    void definirSeuil(Long idArticle, Long idSite, BigDecimal seuil);
}
