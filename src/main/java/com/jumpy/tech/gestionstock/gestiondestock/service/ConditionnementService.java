package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CodeBarresDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ResultatScanDto;

import java.util.List;

/**
 * Les conditionnements d'un article et les codes-barres qui les designent.
 */
public interface ConditionnementService {

    List<ConditionnementDto> conditionnements(Long idArticle);

    ConditionnementDto ajouter(Long idArticle, ConditionnementDto dto);

    ConditionnementDto modifier(Long idArticle, Long idConditionnement, ConditionnementDto dto);

    /**
     * Le conditionnement cesse d'etre propose, et ses codes-barres sont liberes. Il reste en base :
     * les lignes deja vendues le citent.
     */
    void retirer(Long idArticle, Long idConditionnement);

    List<CodeBarresDto> codes(Long idArticle);

    CodeBarresDto ajouterCode(Long idArticle, CodeBarresDto dto);

    /** Tire un EAN-13 interne pour un produit qui n'a pas de code, a l'unite ou dans un conditionnement. */
    CodeBarresDto genererCodeInterne(Long idArticle, Long idConditionnement);

    void retirerCode(Long idArticle, Long idCode);

    /** Ce que designe un code lu par la douchette ou la camera. */
    ResultatScanDto scanner(String code);

    /** Joint a des articles du catalogue leurs conditionnements actifs et leurs codes, en deux requetes. */
    List<ArticleDto> completer(List<ArticleDto> articles);
}
