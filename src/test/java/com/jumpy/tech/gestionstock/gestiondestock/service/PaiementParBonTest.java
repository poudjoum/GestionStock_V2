package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CampagneDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.BonDAchat;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CompteClientFidelite;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutBonDAchat;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.fidelite.CodeBonAchat;
import com.jumpy.tech.gestionstock.gestiondestock.fidelite.TicketsPublics;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Campagnes;
import com.jumpy.tech.gestionstock.gestiondestock.repository.BonDAchatRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CompteClientFideliteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le bon d'achat donne en paiement : il diminue le reste a payer, une seule fois.
 *
 * Le magasin ne collecte pas la TVA : un sac a 5 000 F coute 5 000 F.
 */
class PaiementParBonTest extends AbstractIntegrationTest {

    @Autowired
    private FactureService factureService;
    @Autowired
    private CaisseService caisseService;
    @Autowired
    private TicketsPublics tickets;
    @Autowired
    private Campagnes campagnes;
    @Autowired
    private Calendrier calendrier;
    @Autowired
    private VenteService venteService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    @Autowired
    private BonDAchatRepository bonRepository;
    @Autowired
    private CompteClientFideliteRepository clientRepository;

    private Long idEntreprise;
    private Long ciment;
    private CompteClientFidelite client;

    @BeforeEach
    void unMagasinUnArticleUnClient() {
        idEntreprise = magasin("Quincaillerie du Port");
        connecte(idEntreprise);
        CategoryDto categorie = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        ciment = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Sac de ciment 50 kg")
                .prixUnitaireHt(new BigDecimal("5000"))
                .category(categorie)
                .build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ciment).build())
                .quantite(new BigDecimal("100")).build());

        CompteClientFidelite compte = new CompteClientFidelite();
        compte.setTelephone("6" + String.format("%08d", (System.nanoTime() / 1000) % 100_000_000L));
        compte.setMotDePasse("x");
        client = clientRepository.save(compte);
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void le_bon_diminue_le_reste_a_payer_et_se_consomme() {
        FactureDto facture = factureDe("2");              // 10 000 F
        String code = bon(idEntreprise, "3000");

        ReglementDto reglement = factureService.regler(facture.getId(), parBon(code));

        assertThat(reglement.getMontant()).isEqualByComparingTo("3000");
        assertThat(reglement.getReference()).isEqualTo(code);
        assertThat(factureService.findById(facture.getId()).getResteAPayer()).isEqualByComparingTo("7000");
        BonDAchat consomme = bonRepository.findByCodeBon(code).orElseThrow();
        assertThat(consomme.getStatut()).isEqualTo(StatutBonDAchat.UTILISE);
        assertThat(consomme.getVenteUtilisation()).isNotNull();
    }

    @Test
    void le_bon_n_entre_pas_dans_la_recette_de_la_caisse() {
        FactureDto facture = factureDe("1");
        factureService.regler(facture.getId(), ReglementDto.builder()
                .montant(new BigDecimal("4000")).mode(ModeReglement.ESPECES).build());
        factureService.regler(facture.getId(), parBon(bon(idEntreprise, "1000")));

        var etat = caisseService.etat(null, null);

        assertThat(etat.getTotal()).isEqualByComparingTo("4000");
        assertThat(etat.getParMode()).noneMatch(t -> t.getMode() == ModeReglement.BON_ACHAT);
        assertThat(etat.getBonsAchat().getTotal()).isEqualByComparingTo("1000");
    }

    @Test
    void la_part_reglee_par_bon_ne_rapporte_pas_de_points() {
        // Une campagne en cours, et un point par 1 000 F payes.
        campagnes.creer(CampagneDto.builder().titre("Rentrée")
                .dateDebut(calendrier.aujourdhui()).dateFin(calendrier.aujourdhui().plusDays(7)).build());
        var magasin = entrepriseRepository.findById(idEntreprise).orElseThrow();
        magasin.setMontantParPoint(new BigDecimal("1000"));
        entrepriseRepository.save(magasin);

        FactureDto facture = factureDe("2");              // 10 000 F
        factureService.regler(facture.getId(), parBon(bon(idEntreprise, "3000")));
        factureService.regler(facture.getId(), ReglementDto.builder()
                .montant(new BigDecimal("7000")).mode(ModeReglement.ESPECES).build());

        // 10 000 F de ticket, dont 3 000 F regles par le bon : 7 points, et non 10. Sans cela, le
        // bon rapportait des points comme de l'argent.
        assertThat(tickets.parCode(facture.getCodeTicket()).getPoints()).isEqualTo(7);
    }

    @Test
    void le_bon_ne_rend_pas_la_monnaie() {
        FactureDto facture = factureDe("1");              // 5 000 F
        factureService.regler(facture.getId(), ReglementDto.builder()
                .montant(new BigDecimal("4000")).mode(ModeReglement.ESPECES).build());
        String code = bon(idEntreprise, "3000");

        ReglementDto reglement = factureService.regler(facture.getId(), parBon(code));

        // 1 000 F restaient : le bon les regle, et ses 2 000 F de plus sont perdus.
        assertThat(reglement.getMontant()).isEqualByComparingTo("1000");
        assertThat(bonRepository.findByCodeBon(code).orElseThrow().getStatut()).isEqualTo(StatutBonDAchat.UTILISE);
    }

    @Test
    void un_bon_ne_sert_qu_une_fois() {
        String code = bon(idEntreprise, "1000");
        factureService.regler(factureDe("1").getId(), parBon(code));

        Long autre = factureDe("1").getId();
        assertThatThrownBy(() -> factureService.regler(autre, parBon(code)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("déjà été utilisé");
    }

    @Test
    void un_bon_expire_est_refuse() {
        String code = bon(idEntreprise, "1000");
        BonDAchat bon = bonRepository.findByCodeBon(code).orElseThrow();
        bon.setDateExpiration(Instant.now().minus(1, ChronoUnit.DAYS));
        bonRepository.save(bon);

        Long facture = factureDe("1").getId();
        assertThatThrownBy(() -> factureService.regler(facture, parBon(code)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("expiré");
    }

    @Test
    void le_bon_d_un_autre_magasin_est_inconnu_ici() {
        String code = bon(magasin("Le voisin"), "1000");

        Long facture = factureDe("1").getId();
        assertThatThrownBy(() -> factureService.regler(facture, parBon(code)))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(bonRepository.findByCodeBon(code).orElseThrow().getStatut()).isEqualTo(StatutBonDAchat.ACTIF);
    }

    @Test
    void reprendre_le_reglement_rend_le_bon() {
        FactureDto facture = factureDe("1");
        String code = bon(idEntreprise, "1000");
        ReglementDto reglement = factureService.regler(facture.getId(), parBon(code));

        factureService.supprimerReglement(facture.getId(), reglement.getId());

        BonDAchat rendu = bonRepository.findByCodeBon(code).orElseThrow();
        assertThat(rendu.getStatut()).isEqualTo(StatutBonDAchat.ACTIF);
        assertThat(rendu.getVenteUtilisation()).isNull();
        assertThat(factureService.findById(facture.getId()).getResteAPayer()).isEqualByComparingTo("5000");
    }

    // --- Outils -------------------------------------------------------------------------------

    private FactureDto factureDe(String quantite) {
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(ciment).build())
                        .quantite(new BigDecimal(quantite))
                        .prixUnitaire(new BigDecimal("5000"))
                        .build()))
                .build());
        return factureService.emettre(vente.getId());
    }

    private String bon(Long entreprise, String montant) {
        BonDAchat bon = new BonDAchat();
        bon.setCodeBon(CodeBonAchat.nouveau());
        bon.setClient(client);
        bon.setEntreprise(entrepriseRepository.findById(entreprise).orElseThrow());
        bon.setPointsUtilises(Integer.parseInt(montant));
        bon.setMontantFcfa(new BigDecimal(montant));
        bon.setStatut(StatutBonDAchat.ACTIF);
        bon.setDateEmission(Instant.now());
        bon.setDateExpiration(Instant.now().plus(30, ChronoUnit.DAYS));
        return bonRepository.save(bon).getCodeBon();
    }

    private static ReglementDto parBon(String code) {
        return ReglementDto.builder().mode(ModeReglement.BON_ACHAT).reference(code).build();
    }

    private Long magasin(String nom) {
        return entrepriseService.save(EntrepriseDto.builder()
                .nom(nom)
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .assujettieTva(false)
                .build()).getId();
    }

    private void connecte(Long entreprise) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "caissier", "caissier@exemple.test", "x",
                entreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_CAISSIER.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
