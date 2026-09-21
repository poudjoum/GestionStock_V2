package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AdresseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeNotification;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EnvoiRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Les notifications.
 *
 * Rien ne prevenait personne : un article tombait en rupture et on l'apprenait au comptoir, une
 * facture s'emettait sans que le client le sache.
 *
 * La regle qui tient tout : un echec d'envoi ne fait jamais echouer une operation metier. On
 * enregistre l'intention de notifier dans la transaction de l'operation, et on la livre separement.
 */
class NotificationsTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notifications;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private FactureService factureService;
    @Autowired
    private UserService userService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private EnvoiRepository envoiRepository;

    private Long idEntreprise;
    private String magasinier;
    private String caissier;

    @BeforeEach
    void uneMaisonEtDeuxEmployes() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Quincaillerie " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();

        connecte("amorceur", null, ERole.ROLE_SUPER_ADMIN);
        magasinier = creerCompte(ERole.ROLE_MAGASINIER);
        caissier = creerCompte(ERole.ROLE_CAISSIER);
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    /** Cree un compte rattache a l'entreprise du test et lui donne son role. Rend son identifiant. */
    private String creerCompte(ERole role) {
        String username = "u-" + UUID.randomUUID();
        UserDto compte = userService.save(UserDto.builder()
                .nom("Employé").prenoms(role.name())
                .username(username)
                .email(UUID.randomUUID() + "@exemple.test")
                .motdepasse("MotDePasse123!")
                .numTel("690000000")
                .dateNaissance(Instant.parse("1990-01-01T00:00:00Z"))
                .adresse(AdresseDto.builder().adresse1("Rue 1").ville("Douala").pays("Cameroun").build())
                .entreprise(EntrepriseDto.builder().id(idEntreprise).build())
                .build());
        userService.changerRoles(compte.getId(), List.of(role));
        return username;
    }

    private void connecte(String username, Long entreprise, ERole role) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, username, username + "@exemple.test",
                "x", entreprise, List.of(new SimpleGrantedAuthority(role.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** Un article de l'entreprise du test, avec son seuil d'alerte. */
    private Long unArticle(String seuil) {
        connecte("gerant", idEntreprise, ERole.ROLE_ADMIN);
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        return articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID())
                .designation("Sac de ciment")
                .prixUnitaireHt(new BigDecimal("5000"))
                .seuilAlerte(seuil == null ? null : new BigDecimal(seuil))
                .category(category)
                .build()).getId();
    }

    private void entree(Long idArticle, String quantite) {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal(quantite)).build());
    }

    private void sortie(Long idArticle, String quantite) {
        mvtStkService.sortieStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal(quantite)).build());
    }

    private long nonLuesDu(String username) {
        connecte(username, idEntreprise, ERole.ROLE_MAGASINIER);
        return notifications.compteNonLues();
    }

    // --- L'alerte de stock ----------------------------------------------------------------

    @Test
    void passer_sous_le_seuil_previent_le_magasin() {
        Long article = unArticle("10");
        entree(article, "12");

        sortie(article, "4");

        // La rupture se decouvrait au comptoir, devant le client.
        assertThat(nonLuesDu(magasinier)).isEqualTo(1);
        var recue = notifications.mesNotifications(true, PageRequest.of(0, 10)).getContent().get(0);
        assertThat(recue.getType()).isEqualTo(TypeNotification.STOCK_ALERTE);
        assertThat(recue.getTitre()).startsWith("Sous le seuil");
    }

    @Test
    void la_meme_alerte_ne_se_repete_pas_tant_qu_elle_n_est_pas_lue() {
        Long article = unArticle("10");
        entree(article, "12");

        sortie(article, "1");
        sortie(article, "1");
        sortie(article, "1");

        // Un article sous son seuil le reste a chaque vente : sans cle de regroupement, une
        // journee de comptoir enterrerait la boite aux lettres sous le meme message.
        assertThat(nonLuesDu(magasinier)).isEqualTo(1);
    }

    @Test
    void une_alerte_lue_peut_revenir() {
        Long article = unArticle("10");
        entree(article, "12");
        sortie(article, "1");

        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);
        notifications.marquerToutesLues();
        sortie(article, "1");

        // Lue, elle a ete traitee : la situation qui persiste merite d'etre rappelee.
        assertThat(nonLuesDu(magasinier)).isEqualTo(1);
    }

    @Test
    void une_rupture_et_un_negatif_ne_disent_pas_la_meme_chose() {
        Long article = unArticle(null);
        entree(article, "3");

        sortie(article, "3");
        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);
        var rupture = notifications.mesNotifications(true, PageRequest.of(0, 10)).getContent().get(0);
        assertThat(rupture.getTitre()).startsWith("Rupture");
        notifications.marquerToutesLues();

        // Une vente hors ligne, arrivee apres coup, fait passer le stock sous zero.
        venteService.synchroniser(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .referenceClient(UUID.randomUUID().toString())
                .datevente(Instant.now().minusSeconds(3600))
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(article).build())
                        .quantite(new BigDecimal("2"))
                        .prixUnitaire(new BigDecimal("5000")).build()))
                .build());

        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);
        var negatif = notifications.mesNotifications(true, PageRequest.of(0, 10)).getContent().get(0);
        // Les deux n'appellent pas le meme geste : la rupture se commande, le negatif se compte.
        assertThat(negatif.getTitre()).startsWith("Stock négatif");
        assertThat(negatif.getCorps()).contains("comptage");
    }

    @Test
    void un_article_bien_pourvu_ne_previent_personne() {
        Long article = unArticle("2");
        entree(article, "100");

        sortie(article, "1");

        assertThat(nonLuesDu(magasinier)).isZero();
    }

    @Test
    void une_entree_ne_previent_personne() {
        Long article = unArticle("10");

        // Une entree ne fait jamais baisser le stock : verifier apres chaque reception couterait
        // une requete pour rien.
        entree(article, "1");

        assertThat(nonLuesDu(magasinier)).isZero();
    }

    @Test
    void le_caissier_n_est_pas_prevenu_du_stock() {
        Long article = unArticle("10");
        entree(article, "12");
        sortie(article, "4");

        // Commander au fournisseur n'est pas son metier ; l'avertir ne ferait que du bruit.
        connecte(caissier, idEntreprise, ERole.ROLE_CAISSIER);
        assertThat(notifications.compteNonLues()).isZero();
    }

    // --- Lire ses notifications -------------------------------------------------------------

    @Test
    void marquer_lue_fait_baisser_le_compte() {
        Long article = unArticle("10");
        entree(article, "12");
        sortie(article, "4");

        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);
        var recue = notifications.mesNotifications(true, PageRequest.of(0, 10)).getContent().get(0);
        notifications.marquerLue(recue.getId());

        assertThat(notifications.compteNonLues()).isZero();
        assertThat(notifications.mesNotifications(false, PageRequest.of(0, 10)).getContent())
                .hasSize(1);
        assertThat(notifications.mesNotifications(true, PageRequest.of(0, 10)).getContent())
                .isEmpty();
    }

    @Test
    void la_notification_d_un_autre_est_introuvable() {
        Long article = unArticle("10");
        entree(article, "12");
        sortie(article, "4");

        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);
        Long idAutrui = notifications.mesNotifications(true, PageRequest.of(0, 10))
                .getContent().get(0).getId();

        // Introuvable, jamais interdite : un 403 confirmerait qu'elle existe.
        connecte(caissier, idEntreprise, ERole.ROLE_CAISSIER);
        assertThatThrownBy(() -> notifications.marquerLue(idAutrui))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void marquer_lue_deux_fois_ne_change_rien() {
        Long article = unArticle("10");
        entree(article, "12");
        sortie(article, "4");

        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);
        var recue = notifications.mesNotifications(true, PageRequest.of(0, 10)).getContent().get(0);
        var premiere = notifications.marquerLue(recue.getId());
        var seconde = notifications.marquerLue(recue.getId());

        // A la milliseconde : `Instant` compte en nanosecondes, PostgreSQL en microsecondes, et
        // la premiere valeur est encore celle qu'on vient d'ecrire quand la seconde revient de
        // la base. Ce que le test verifie est que la date n'a pas ete repoussee, pas le dernier
        // chiffre de son arrondi.
        assertThat(seconde.getLuLe()).isCloseTo(premiere.getLuLe(), within(1, ChronoUnit.MILLIS));
    }

    // --- Les courriels mis en file ----------------------------------------------------------

    @Test
    void une_facture_met_un_courriel_en_file_pour_le_client() {
        Long article = unArticle(null);
        entree(article, "10");
        connecte("gerant", idEntreprise, ERole.ROLE_ADMIN);
        ClientDto client = clientService.save(ClientDto.builder()
                .nom("Chantier").prenoms("Nord")
                .mail("chantier" + UUID.randomUUID() + "@exemple.test")
                .numTel("690111222").build());
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .client(client)
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(article).build())
                        .quantite(new BigDecimal("2"))
                        .prixUnitaire(new BigDecimal("5000")).build()))
                .build());

        long avant = envoiRepository.count();
        var facture = factureService.emettre(vente.getId());

        assertThat(envoiRepository.count()).isEqualTo(avant + 1);
        var envoi = envoiRepository.findAll().stream()
                .filter(e -> e.getDestination().equals(client.getMail()))
                .findFirst();
        assertThat(envoi).isPresent();
        assertThat(envoi.get().getEtat()).isEqualTo(EtatEnvoi.A_ENVOYER);
        assertThat(envoi.get().getSujet()).contains(facture.getNumero());
    }

    @Test
    void une_facture_sans_client_ne_met_rien_en_file() {
        Long article = unArticle(null);
        entree(article, "10");
        connecte("gerant", idEntreprise, ERole.ROLE_ADMIN);
        VenteDto vente = venteService.save(VenteDto.builder()
                .code("V-" + UUID.randomUUID())
                .ligneVente(List.of(LigneVenteDto.builder()
                        .article(ArticleDto.builder().Id(article).build())
                        .quantite(new BigDecimal("1"))
                        .prixUnitaire(new BigDecimal("5000")).build()))
                .build());

        long avant = envoiRepository.count();
        factureService.emettre(vente.getId());

        // Le ticket de caisse anonyme reste le cas ordinaire : ce n'est pas une anomalie.
        assertThat(envoiRepository.count()).isEqualTo(avant);
    }

    @Test
    void ouvrir_un_compte_met_un_courriel_en_file() {
        connecte("gerant", idEntreprise, ERole.ROLE_ADMIN);
        long avant = envoiRepository.count();

        String username = creerCompte(ERole.ROLE_CAISSIER);

        assertThat(envoiRepository.count()).isGreaterThan(avant);
        var envoi = envoiRepository.findAll().stream()
                .filter(e -> e.getCorps().contains(username))
                .findFirst();
        assertThat(envoi).isPresent();
        // Le mot de passe n'y figure pas : il est chiffre avant d'arriver la, et personne ne le
        // connait plus en clair.
        assertThat(envoi.get().getCorps()).doesNotContain("MotDePasse123!");
    }
}
