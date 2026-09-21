package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La recherche dans les listes.
 *
 * Les listes etaient paginees mais pas filtrables : `/articles?page=3` rendait la page 3 de tout
 * le catalogue. Un caissier qui tape « cim » pour trouver « Sac de ciment » n'avait rien, et sur
 * un telephone c'est la difference entre utilisable et inutilisable.
 */
class RechercheDansLesListesTest extends AbstractIntegrationTest {

    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private FournisseurService fournisseurService;

    private String suffixe;
    private Long idCategorie;
    private Long idAutreCategorie;

    @BeforeEach
    void unCatalogue() {
        suffixe = UUID.randomUUID().toString().substring(0, 8);
        idCategorie = categoryService.save(CategoryDto.builder()
                .codeCategorie("MAT-" + suffixe).designation("Matériaux").build()).getId();
        idAutreCategorie = categoryService.save(CategoryDto.builder()
                .codeCategorie("OUT-" + suffixe).designation("Outillage").build()).getId();
    }

    private void article(String code, String designation, Long idCategory) {
        articleService.save(ArticleDto.builder()
                .codeArticle(code + "-" + suffixe)
                .designation(designation)
                .prixUnitaireHt(new BigDecimal("1000"))
                .category(CategoryDto.builder().Id(idCategory).build())
                .build());
    }

    @Test
    void chercher_par_un_bout_de_designation() {
        article("ART1", "Sac de ciment " + suffixe, idCategorie);
        article("ART2", "Tôle ondulée " + suffixe, idCategorie);

        var trouves = articleService.findAll("ciment " + suffixe, null, PageRequest.of(0, 20));

        assertThat(trouves.getContent()).hasSize(1);
        assertThat(trouves.getContent().get(0).getDesignation()).contains("Sac de ciment");
    }

    @Test
    void chercher_par_un_bout_de_code() {
        article("CIM01", "Sac de ciment " + suffixe, idCategorie);
        article("TOL01", "Tôle ondulée " + suffixe, idCategorie);

        var trouves = articleService.findAll("cim01-" + suffixe, null, PageRequest.of(0, 20));

        assertThat(trouves.getContent()).hasSize(1);
        assertThat(trouves.getContent().get(0).getCodeArticle()).startsWith("CIM01");
    }

    @Test
    void la_casse_ne_compte_pas() {
        article("ART1", "Sac de CIMENT " + suffixe, idCategorie);

        // Le clavier d'un telephone met une majuscule au premier mot : exiger la casse exacte
        // rendrait la recherche inutilisable la ou elle sert le plus.
        assertThat(articleService.findAll("sac de ciment " + suffixe, null, PageRequest.of(0, 20))
                .getContent()).hasSize(1);
    }

    @Test
    void les_espaces_de_bord_ne_comptent_pas() {
        article("ART1", "Sac de ciment " + suffixe, idCategorie);

        // Le clavier d'un telephone ajoute un espace apres chaque mot.
        assertThat(articleService.findAll("  ciment " + suffixe + "  ", null, PageRequest.of(0, 20))
                .getContent()).hasSize(1);
    }

    @Test
    void une_recherche_vide_ne_filtre_rien() {
        article("ART1", "Sac de ciment " + suffixe, idCategorie);
        article("ART2", "Tôle ondulée " + suffixe, idCategorie);

        // Le navigateur envoie « ?q= » quand le champ est vide : une chaine vide comparee en
        // « like '%%' » passerait pour un filtre alors qu'elle n'en est pas un.
        long avecVide = articleService.findAll("", null, PageRequest.of(0, 500)).getTotalElements();
        long sansRien = articleService.findAll(null, null, PageRequest.of(0, 500)).getTotalElements();

        assertThat(avecVide).isEqualTo(sansRien).isGreaterThanOrEqualTo(2);
    }

    @Test
    void filtrer_par_categorie() {
        article("ART1", "Sac de ciment " + suffixe, idCategorie);
        article("ART2", "Marteau " + suffixe, idAutreCategorie);

        var trouves = articleService.findAll(suffixe, idAutreCategorie, PageRequest.of(0, 20));

        assertThat(trouves.getContent()).hasSize(1);
        assertThat(trouves.getContent().get(0).getDesignation()).startsWith("Marteau");
    }

    @Test
    void la_recherche_garde_le_compte_total() {
        for (int i = 0; i < 5; i++) {
            article("ART" + i, "Sac de ciment " + i + " " + suffixe, idCategorie);
        }

        var page = articleService.findAll("ciment", null, PageRequest.of(0, 2));

        // `map` sur la Page conserve le total : sans lui, le front ne sait pas s'il reste des
        // pages a demander.
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void chercher_un_client_par_son_numero() {
        // Un numero propre a ce test : la base est partagee par toute la campagne, et un numero
        // fixe serait retrouve par les clients que les autres tests y ont laisses.
        String numero = numeroUnique();
        clientService.save(ClientDto.builder()
                .nom("Chantier " + suffixe).prenoms("X")
                .mail(suffixe + "@exemple.test").numTel(numero).build());
        clientService.save(ClientDto.builder()
                .nom("Boutique " + suffixe).prenoms("Y")
                .mail("autre" + suffixe + "@exemple.test").numTel(numeroUnique()).build());

        // C'est le numero qu'on demande a un client qu'on ne retrouve pas dans la liste.
        var trouves = clientService.findAll(numero, PageRequest.of(0, 20));

        assertThat(trouves.getContent()).hasSize(1);
        assertThat(trouves.getContent().get(0).getNom()).startsWith("Chantier");
    }

    @Test
    void chercher_un_client_par_son_courriel() {
        clientService.save(ClientDto.builder()
                .nom("Chantier " + suffixe).prenoms("X")
                .mail(suffixe + "@exemple.test").numTel(numeroUnique()).build());

        assertThat(clientService.findAll(suffixe + "@exemple", PageRequest.of(0, 20)).getContent())
                .hasSize(1);
    }

    /** Neuf chiffres tires au hasard, jamais deux fois le meme dans une campagne. */
    private static String numeroUnique() {
        return String.valueOf(600_000_000 + (int) (Math.random() * 99_000_000));
    }

    @Test
    void chercher_un_fournisseur_par_son_nom_ou_son_courriel() {
        String numero = numeroUnique();
        fournisseurService.save(FournisseurDto.builder()
                .nom("Cimenterie " + suffixe).prenom("X")
                .mail("contact" + suffixe + "@exemple.test").tel(numero).build());
        fournisseurService.save(FournisseurDto.builder()
                .nom("Quincaillerie " + suffixe).prenom("Y")
                .mail("autre" + suffixe + "@exemple.test").tel(numeroUnique()).build());

        assertThat(fournisseurService.findAll("cimenterie " + suffixe, PageRequest.of(0, 20))
                .getContent()).hasSize(1);
        assertThat(fournisseurService.findAll("contact" + suffixe, PageRequest.of(0, 20))
                .getContent()).hasSize(1);
        assertThat(fournisseurService.findAll(numero, PageRequest.of(0, 20))
                .getContent()).hasSize(1);
    }

    @Test
    void chercher_ce_qui_n_existe_pas_rend_une_page_vide() {
        article("ART1", "Sac de ciment " + suffixe, idCategorie);

        var trouves = articleService.findAll("tracteur-" + UUID.randomUUID(), null, PageRequest.of(0, 20));

        // Une page vide, pas une erreur : ne rien trouver est un resultat.
        assertThat(trouves.getContent()).isEmpty();
        assertThat(trouves.getTotalElements()).isZero();
    }
}
