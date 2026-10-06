package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.rapport.ResumesParCourriel;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EnvoiRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.RoleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** R4 : le resume du matin, a qui il part, et qu'il ne part qu'une fois. */
class ResumesParCourrielTest extends AbstractIntegrationTest {

    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private EnvoiRepository envoiRepository;
    @Autowired
    private ResumesParCourriel resumes;
    @Autowired
    private Calendrier calendrier;

    private Long idEntreprise;
    private String nomDuCommerce;
    private String adresseDuGerant;
    private LocalDate hier;

    @BeforeEach
    void unCommerceQuiAVenduHier() {
        nomDuCommerce = "Boutique Bonapriso " + UUID.randomUUID().toString().substring(0, 6);
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom(nomDuCommerce)
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        Entreprise entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow();
        entreprise.setResumeQuotidien(true);
        entrepriseRepository.save(entreprise);

        adresseDuGerant = "gerant" + UUID.randomUUID() + "@exemple.test";
        compte(adresseDuGerant, ERole.ROLE_MANAGER, entreprise);
        compte("caissier" + UUID.randomUUID() + "@exemple.test", ERole.ROLE_CAISSIER, entreprise);

        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "g@exemple.test", "x", idEntreprise,
                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        principal.setIdsSites(Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        CategoryDto rayon = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Boissons").build());
        Long idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID()).designation("Jus de bissap")
                .prixUnitaireHt(new BigDecimal("700")).category(rayon).build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder().article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("50")).build());

        hier = calendrier.aujourdhui().minusDays(1);
        // Une vente faite hier, synchronisee depuis le poste : elle porte sa date.
        venteService.synchroniser(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .referenceClient(UUID.randomUUID().toString())
                .datevente(hier.atTime(11, 0).atZone(calendrier.fuseau()).toInstant())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("4")).build()))
                .build());
        // Le matin, personne n'est connecte.
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void oublier() {
        SecurityContextHolder.clearContext();
    }

    private void compte(String email, ERole role, Entreprise entreprise) {
        Utilisateur u = new Utilisateur(email.substring(0, email.indexOf('@')), email, "x");
        u.setEntreprise(entreprise);
        u.setRoles(Set.of(roleRepository.findByRoleName(role).orElseThrow()));
        utilisateurRepository.save(u);
    }

    private List<Envoi> courriels() {
        return envoiRepository.findAll().stream()
                .filter(e -> Objects.equals(e.getIdEntreprise(), idEntreprise))
                .toList();
    }

    @Test
    void le_resume_de_la_veille_part_au_gerant_une_seule_fois() {
        resumes.envoyer(hier.plusDays(1));

        assertThat(courriels()).singleElement().satisfies(e -> {
            assertThat(e.getDestination()).isEqualTo(adresseDuGerant);
            assertThat(e.getSujet()).startsWith("Vos ventes du ").endsWith(nomDuCommerce);
            assertThat(e.getCorps()).contains("Chiffre d'affaires HT : 2").contains("Boissons");
            assertThat(e.getCorpsHtml()).contains("Vos ventes d’hier").contains("Boissons")
                    .contains("<table role=\"presentation\"");
        });

        // Le serveur redemarre a 7 h 01 : rien ne repart.
        resumes.envoyer(hier.plusDays(1));
        assertThat(courriels()).hasSize(1);
    }

    @Test
    void une_journee_sans_vente_n_envoie_rien() {
        resumes.envoyer(hier.plusDays(20));
        assertThat(courriels()).isEmpty();
    }

    @Test
    void le_lundi_la_semaine_ecoulee_part() {
        LocalDate lundi = hier.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        resumes.envoyer(lundi);

        assertThat(courriels()).filteredOn(e -> e.getSujet().startsWith("Votre semaine"))
                .singleElement().satisfies(e -> {
                    assertThat(e.getCorpsHtml()).contains("Votre semaine");
                });
    }
}
