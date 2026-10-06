package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportVentesDto;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.RapportVentesService;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** R1 : le chiffre d'affaires, la marge et ou ils se font. */
class RapportVentesTest extends AbstractIntegrationTest {

    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private FournisseurService fournisseurService;
    @Autowired
    private CommandeFourService commandeFourService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private LigneVenteRepository ligneVenteRepository;
    @Autowired
    private RapportVentesService rapportVentesService;
    @Autowired
    private Calendrier calendrier;

    private Long idArticle;
    private FournisseurDto fournisseur;
    private LocalDate aujourdhui;

    @BeforeEach
    void unArticleAchete400() {
        Long idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Boutique " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "g@exemple.test", "x", idEntreprise,
                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        principal.setIdsSites(Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        aujourdhui = calendrier.aujourdhui();

        CategoryDto boissons = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Boissons").build());
        idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID()).designation("Jus d'orange")
                .prixUnitaireHt(new BigDecimal("1000")).category(boissons).build()).getId();
        fournisseur = fournisseurService.save(FournisseurDto.builder().nom("Grossiste")
                .mail("g" + UUID.randomUUID() + "@exemple.test").tel("690000011").build());
        acheter("10", "400");
    }

    @AfterEach
    void oublier() {
        SecurityContextHolder.clearContext();
    }

    private void acheter(String quantite, String prix) {
        CommandeFourDto achat = commandeFourService.save(CommandeFourDto.builder()
                .code("CF-" + UUID.randomUUID()).dateCommande(Instant.now()).fournisseur(fournisseur)
                .ligneCmndeFournisseur(List.of(LigneCmndeFournisseurDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite)).prixUnitaire(new BigDecimal(prix)).build()))
                .build());
        commandeFourService.mettreAJourEtat(achat.getId(), EtatCommande.VALIDEE);
        LigneReceptionDto reception = new LigneReceptionDto();
        reception.setIdLigne(commandeFourService.lignes(achat.getId()).get(0).getId());
        reception.setQuantite(new BigDecimal(quantite));
        commandeFourService.recevoir(achat.getId(), List.of(reception));
    }

    private VenteDto vendre(String quantite) {
        return venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal(quantite)).build()))
                .build());
    }

    private RapportVentesDto aujourdhui() {
        return rapportVentesService.ventes(aujourdhui, aujourdhui, false);
    }

    @Test
    void la_marge_reste_celle_du_cout_fige_a_la_vente() {
        vendre("3");
        // Un achat plus cher apres la vente ne change pas ce qu'elle a rapporte.
        acheter("10", "700");

        RapportVentesDto.Indicateurs courant = aujourdhui().getCourant();
        assertThat(courant.getChiffreAffaires()).isEqualByComparingTo("3000");
        assertThat(courant.getMarge()).isEqualByComparingTo("1800");
        assertThat(courant.getTauxMarge()).isEqualByComparingTo("60.0");
        assertThat(courant.getTickets()).isEqualTo(1);
        assertThat(courant.getPanierMoyen()).isEqualByComparingTo("3000");
        assertThat(courant.isEstimee()).isFalse();
    }

    @Test
    void une_vente_d_avant_le_cout_fige_a_une_marge_estimee() {
        VenteDto vente = vendre("2");
        // Comme une vente d'avant le reporting : pas de cout sur la ligne.
        ligneVenteRepository.findAllByVenteId(vente.getId()).forEach(l -> {
            l.setCoutUnitaire(null);
            ligneVenteRepository.save(l);
        });

        RapportVentesDto.Indicateurs courant = aujourdhui().getCourant();
        assertThat(courant.isEstimee()).isTrue();
        assertThat(courant.getMarge()).isEqualByComparingTo("1200");
    }

    @Test
    void une_vente_annulee_ne_compte_pas_et_tout_se_decoupe() {
        vendre("1");
        venteService.annuler(vendre("5").getId());

        RapportVentesDto rapport = aujourdhui();
        assertThat(rapport.getCourant().getChiffreAffaires()).isEqualByComparingTo("1000");
        assertThat(rapport.getPas()).isEqualTo("JOUR");
        assertThat(rapport.getSerie()).singleElement()
                .satisfies(p -> assertThat(p.getChiffreAffaires()).isEqualByComparingTo("1000"));
        assertThat(rapport.getParHeure()).hasSize(24);
        assertThat(rapport.getParJourSemaine()).hasSize(7);
        assertThat(rapport.getParCategorie()).singleElement().satisfies(c -> {
            assertThat(c.getLibelle()).isEqualTo("Boissons");
            assertThat(c.getPart()).isEqualByComparingTo("100.0");
        });
        assertThat(rapport.getParSite()).singleElement()
                .satisfies(s -> assertThat(s.getLibelle()).isEqualTo("Magasin principal"));
        // Le compte du test n'existe pas en base : la vente est notee, son nom introuvable.
        assertThat(rapport.getParVendeur()).singleElement()
                .satisfies(v -> assertThat(v.getId()).isEqualTo(1L));
    }

    @Test
    void la_comparaison_porte_sur_la_periode_precedente_et_l_an_dernier() {
        vendre("1");
        RapportVentesDto rapport = rapportVentesService.ventes(aujourdhui.minusDays(6), aujourdhui, false);

        assertThat(rapport.getSerie()).hasSize(7);
        assertThat(rapport.getSeriePrecedente()).hasSize(7);
        assertThat(rapport.getPrecedent().getFin()).isEqualTo(aujourdhui.minusDays(7));
        assertThat(rapport.getPrecedent().getChiffreAffaires()).isEqualByComparingTo("0");
        assertThat(rapport.getAnneePrecedente().getDebut()).isEqualTo(aujourdhui.minusDays(6).minusYears(1));

        // Plus de deux mois : la courbe passe au mois.
        assertThat(rapportVentesService.ventes(aujourdhui.minusDays(100), aujourdhui, false).getPas()).isEqualTo("MOIS");
    }
}
