package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDeCaisseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'etat de caisse : ce qui est entre, et par quel moyen.
 *
 * Les encaissements existaient sans que rien ne les additionne — savoir ce qui etait entre dans la
 * journee demandait d'ouvrir les factures une par une.
 */
class EtatDeCaisseTest extends AbstractIntegrationTest {

    @Autowired
    private CaisseService caisseService;
    @Autowired
    private FactureService factureService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private MvtStkService mvtStkService;

    @Value("${app.fuseauHoraire:Africa/Douala}")
    private String fuseauHoraire;

    private Long idEntrepriseA;
    private Long idEntrepriseB;

    @BeforeEach
    void deuxEntreprises() {
        idEntrepriseA = creerEntreprise();
        idEntrepriseB = creerEntreprise();
    }

    @AfterEach
    void oublierLUtilisateur() {
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

    private void connecteChez(Long idEntreprise) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "caissier", "caissier@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** Une facture de 1 000 HT sans TVA, reglee du montant demande par le mode donne. */
    private void encaisser(String montant, ModeReglement mode) {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        Long idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Article")
                .prixUnitaireHt(new BigDecimal("100000"))
                .tauxTva(BigDecimal.ZERO)
                .category(category)
                .build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("100")).build());

        VenteDto vente = venteService.save(VenteDto.builder()
                .code("VTE-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(BigDecimal.ONE)
                        .prixUnitaire(new BigDecimal("100000"))
                        .build()))
                .build());
        FactureDto facture = factureService.emettre(vente.getId());
        factureService.regler(facture.getId(), ReglementDto.builder()
                .montant(new BigDecimal(montant)).mode(mode).build());
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(ZoneId.of(fuseauHoraire));
    }

    @Test
    void la_caisse_du_jour_additionne_les_encaissements_par_mode() {
        connecteChez(idEntrepriseA);
        encaisser("5000", ModeReglement.ESPECES);
        encaisser("3000", ModeReglement.ESPECES);
        encaisser("7000", ModeReglement.MOBILE_MONEY);

        EtatDeCaisseDto etat = caisseService.etat(null, null);

        assertThat(etat.getDebut()).isEqualTo(aujourdhui());
        assertThat(etat.getTotal()).isEqualByComparingTo("15000");
        assertThat(etat.getNombreReglements()).isEqualTo(3);
        assertThat(etat.getParMode())
                .extracting(EtatDeCaisseDto.TotalParModeDto::getMode)
                .containsExactlyInAnyOrder(ModeReglement.ESPECES, ModeReglement.MOBILE_MONEY);
        assertThat(etat.getParMode()).filteredOn(l -> l.getMode() == ModeReglement.ESPECES)
                .singleElement()
                .satisfies(especes -> {
                    // Ce qui doit se retrouver dans le tiroir.
                    assertThat(especes.getTotal()).isEqualByComparingTo("8000");
                    assertThat(especes.getNombre()).isEqualTo(2);
                });
    }

    @Test
    void le_total_est_la_somme_des_lignes_affichees() {
        connecteChez(idEntrepriseA);
        encaisser("1200", ModeReglement.VIREMENT);
        encaisser("800", ModeReglement.CHEQUE);

        EtatDeCaisseDto etat = caisseService.etat(null, null);

        BigDecimal sommeDesLignes = etat.getParMode().stream()
                .map(EtatDeCaisseDto.TotalParModeDto::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(etat.getTotal()).isEqualByComparingTo(sommeDesLignes);
    }

    @Test
    void une_caisse_sans_encaissement_vaut_zero_et_non_rien() {
        connecteChez(idEntrepriseA);

        EtatDeCaisseDto etat = caisseService.etat(null, null);

        assertThat(etat.getTotal()).isEqualByComparingTo("0");
        assertThat(etat.getNombreReglements()).isZero();
        assertThat(etat.getParMode()).isEmpty();
    }

    @Test
    void la_caisse_d_une_entreprise_ignore_celle_de_l_autre() {
        connecteChez(idEntrepriseA);
        encaisser("5000", ModeReglement.ESPECES);

        connecteChez(idEntrepriseB);
        encaisser("9000", ModeReglement.ESPECES);

        assertThat(caisseService.etat(null, null).getTotal()).isEqualByComparingTo("9000");
        connecteChez(idEntrepriseA);
        assertThat(caisseService.etat(null, null).getTotal()).isEqualByComparingTo("5000");
    }

    @Test
    void une_journee_passee_ne_montre_pas_les_encaissements_d_aujourd_hui() {
        connecteChez(idEntrepriseA);
        encaisser("5000", ModeReglement.ESPECES);

        LocalDate hier = aujourdhui().minusDays(1);
        assertThat(caisseService.etat(hier, hier).getTotal()).isEqualByComparingTo("0");

        // La borne haute inclut la journee entiere : d'hier a aujourd'hui, l'encaissement compte.
        assertThat(caisseService.etat(hier, aujourdhui()).getTotal()).isEqualByComparingTo("5000");
    }

    @Test
    void une_periode_a_l_envers_est_refusee() {
        connecteChez(idEntrepriseA);

        assertThatThrownBy(() -> caisseService.etat(aujourdhui(), aujourdhui().minusDays(3)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("précède son début");
    }

    @Test
    void le_detail_permet_de_confronter_le_total_a_ce_qui_le_compose() {
        connecteChez(idEntrepriseA);
        encaisser("2500", ModeReglement.ESPECES);
        encaisser("1500", ModeReglement.MOBILE_MONEY);

        var detail = caisseService.reglements(null, null, PageRequest.of(0, 50));

        assertThat(detail.getTotalElements()).isEqualTo(2);
        BigDecimal sommeDuDetail = detail.getContent().stream()
                .map(ReglementDto::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sommeDuDetail).isEqualByComparingTo(caisseService.etat(null, null).getTotal());
    }
}
