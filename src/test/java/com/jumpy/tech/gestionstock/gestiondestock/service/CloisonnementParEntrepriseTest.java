package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le cloisonnement par entreprise.
 *
 * `id_entreprise` existait depuis le premier jour sur presque toutes les tables sans que rien ne
 * le renseigne ni ne filtre dessus : deux entreprises partageant cette base voyaient les articles,
 * les clients et les ventes l'une de l'autre. Ces tests ouvrent deux entreprises cote a cote et
 * verifient qu'aucune ne voit l'autre.
 */
class CloisonnementParEntrepriseTest extends AbstractIntegrationTest {

    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private EntrepriseService entrepriseService;

    private Long idEntrepriseA;
    private Long idEntrepriseB;

    @BeforeEach
    void deuxEntreprises() {
        idEntrepriseA = creerEntreprise();
        idEntrepriseB = creerEntreprise();
    }

    @AfterEach
    void oublierLUtilisateur() {
        // Le contexte de securite est porte par le fil d'execution, qui est reutilise : sans ce
        // nettoyage, le test suivant heriterait de l'utilisateur du precedent.
        SecurityContextHolder.clearContext();
    }

    private Long creerEntreprise() {
        return entrepriseService.save(EntrepriseDto.builder()
                .nom("Entreprise " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
    }

    /** Fait comme si un employe de cette entreprise etait connecte. */
    private void connecteChez(Long idEntreprise, ERole role) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "employe", "employe@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(role.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Long creerArticle(String designation) {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Categorie")
                .build());
        return articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation(designation)
                .prixUnitaireHt(new BigDecimal("1000"))
                .category(category)
                .build()).getId();
    }

    @Test
    void un_article_cree_recoit_l_entreprise_du_compte_connecte() {
        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);

        Long id = creerArticle("Article de A");

        assertThat(articleService.findById(id).getIdEntreprise()).isEqualTo(idEntrepriseA);
    }

    @Test
    void l_entreprise_envoyee_dans_la_requete_est_ignoree() {
        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Categorie")
                .build());

        // L'appelant pretend travailler pour B : c'est son compte qui decide, pas sa requete.
        Long id = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Tentative")
                .prixUnitaireHt(new BigDecimal("1000"))
                .category(category)
                .idEntreprise(idEntrepriseB)
                .build()).getId();

        assertThat(articleService.findById(id).getIdEntreprise()).isEqualTo(idEntrepriseA);
    }

    @Test
    void une_entreprise_ne_voit_pas_les_articles_de_l_autre() {
        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        creerArticle("Article de A");
        connecteChez(idEntrepriseB, ERole.ROLE_ADMIN);
        creerArticle("Article de B");

        assertThat(articleService.findAll())
                .extracting(ArticleDto::getDesignation)
                .containsOnly("Article de B");

        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        assertThat(articleService.findAll())
                .extracting(ArticleDto::getDesignation)
                .containsOnly("Article de A");
    }

    @Test
    void lire_l_article_d_une_autre_entreprise_rend_un_404_et_non_un_403() {
        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        Long idChezA = creerArticle("Article de A");

        connecteChez(idEntrepriseB, ERole.ROLE_ADMIN);

        // 404 et non 403 : repondre « interdit » confirmerait l'existence de l'article, et
        // permettrait de deviner le catalogue du voisin en essayant des identifiants.
        assertThatThrownBy(() -> articleService.findById(idChezA))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void supprimer_l_article_d_une_autre_entreprise_est_refuse() {
        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        Long idChezA = creerArticle("Article de A");

        connecteChez(idEntrepriseB, ERole.ROLE_ADMIN);
        assertThatThrownBy(() -> articleService.delete(idChezA))
                .isInstanceOf(EntityNotFoundException.class);

        // Il est toujours la, chez A.
        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        assertThat(articleService.findById(idChezA)).isNotNull();
    }

    @Test
    void deux_entreprises_peuvent_employer_le_meme_code_d_article() {
        String code = "ART-COMMUN-" + UUID.randomUUID();

        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        CategoryDto categoryA = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("A").build());
        articleService.save(ArticleDto.builder().codeArticle(code).designation("Chez A")
                .prixUnitaireHt(new BigDecimal("1000")).category(categoryA).build());

        connecteChez(idEntrepriseB, ERole.ROLE_ADMIN);
        CategoryDto categoryB = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("B").build());
        articleService.save(ArticleDto.builder().codeArticle(code).designation("Chez B")
                .prixUnitaireHt(new BigDecimal("2000")).category(categoryB).build());

        // Chacune retrouve le sien : la recherche par code est cloisonnee des la requete.
        assertThat(articleService.findByCodeArticle(code).getDesignation()).isEqualTo("Chez B");
        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        assertThat(articleService.findByCodeArticle(code).getDesignation()).isEqualTo("Chez A");
    }

    @Test
    void les_clients_sont_cloisonnes_comme_le_reste() {
        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        clientService.save(ClientDto.builder().nom("Client de A").prenoms("X")
                .mail("a" + UUID.randomUUID() + "@x.test").numTel("690").build());

        connecteChez(idEntrepriseB, ERole.ROLE_ADMIN);
        assertThat(clientService.findAll())
                .extracting(ClientDto::getNom)
                .doesNotContain("Client de A");
    }

    @Test
    void chacun_lit_l_entreprise_pour_laquelle_il_travaille() {
        connecteChez(idEntrepriseA, ERole.ROLE_CAISSIER);

        // Le caissier imprime des tickets a l'en-tete du magasin : il lui faut le nom, l'adresse
        // et le registre de commerce de sa maison. Il n'a pas a pouvoir nommer une entreprise
        // pour cela — c'est son jeton qui la designe.
        assertThat(entrepriseService.mienne().getId()).isEqualTo(idEntrepriseA);

        connecteChez(idEntrepriseB, ERole.ROLE_CAISSIER);
        assertThat(entrepriseService.mienne().getId()).isEqualTo(idEntrepriseB);
    }

    @Test
    void un_compte_sans_entreprise_n_en_a_aucune_a_rendre() {
        connecteChez(null, ERole.ROLE_SUPER_ADMIN);

        // Rien a montrer, et non un droit qui manque : le super-administrateur ne travaille pour
        // aucune maison en particulier.
        assertThatThrownBy(() -> entrepriseService.mienne())
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("n'est rattaché à aucune entreprise");
    }

    @Test
    void le_super_administrateur_voit_toutes_les_entreprises() {
        connecteChez(idEntrepriseA, ERole.ROLE_ADMIN);
        creerArticle("Article de A");
        connecteChez(idEntrepriseB, ERole.ROLE_ADMIN);
        creerArticle("Article de B");

        // Sans entreprise, mais super-administrateur : il cree les entreprises, il regarde
        // au-dela de chacune.
        connecteChez(null, ERole.ROLE_SUPER_ADMIN);

        assertThat(articleService.findAll())
                .extracting(ArticleDto::getDesignation)
                .contains("Article de A", "Article de B");
    }
}
