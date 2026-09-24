package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AdresseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommerceDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.InscriptionEntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutAbonnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EnvoiRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La plateforme : les commerces heberges, leur abonnement, et la porte qu'on leur ferme.
 *
 * L'application savait tenir un magasin ; elle ne savait pas qu'elle en hebergeait plusieurs.
 * L'editeur n'avait aucun moyen de voir ses clients ni de fermer l'acces a celui qui ne paie plus.
 *
 * `@Transactional` sur la classe : les entreprises et les comptes crees ici ne restent pas dans la
 * base que toute la campagne partage, et les relations chargees en LAZY se lisent en session.
 */
@Transactional
class PlateformeTest extends AbstractIntegrationTest {

    private static final String MOT_DE_PASSE = "Provisoire!2026";

    @Autowired
    private PlateformeService plateformeService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private UserService userService;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private EnvoiRepository envoiRepository;

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private void connecte(Long idEntreprise, ERole role) {
        UserDetailsImpl principal = new UserDetailsImpl(1L, "essai", "essai@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(role.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** L'editeur : au-dessus des entreprises, rattache a aucune. */
    private void editeur() {
        connecte(null, ERole.ROLE_SUPER_ADMIN);
    }

    private InscriptionEntrepriseDto inscription(String suffixe) {
        InscriptionEntrepriseDto inscription = new InscriptionEntrepriseDto();
        inscription.setEntreprise(EntrepriseDto.builder()
                        .nom("Quincaillerie " + suffixe)
                        .registreCommerce("RC-" + suffixe)
                        .email("contact-" + suffixe + "@exemple.test")
                        .tel("690000000")
                .adresse(AdresseDto.builder().adresse1("Rue de la Réunification").ville("Douala").pays("Cameroun").build())
                .build());
        inscription.setAdministrateur(UserDto.builder()
                        .nom("Gérant")
                        .prenoms("Le")
                        .username("gerant-" + suffixe)
                        .email("gerant-" + suffixe + "@exemple.test")
                        .motdepasse(MOT_DE_PASSE)
                        .numTel("690000001")
                        .dateNaissance(Instant.parse("1990-05-14T00:00:00Z"))
                .adresse(AdresseDto.builder().adresse1("Rue de la Réunification").ville("Douala").pays("Cameroun").build())
                .build());
        return inscription;
    }

    private CommerceDto ligneDe(Long idEntreprise) {
        return plateformeService.commerces().stream()
                .filter(c -> c.id().equals(idEntreprise))
                .findFirst().orElseThrow();
    }

    private boolean laPorteEstOuverte(String username) {
        Utilisateur compte = utilisateurRepository.findUtilisateurByUsername(username).orElseThrow();
        return UserDetailsImpl.build(compte).isEnabled();
    }

    @Test
    void l_inscription_ouvre_un_abonnement_d_un_an() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);

        EntrepriseDto inscrite = entrepriseService.inscrire(inscription(suffixe));

        CommerceDto ligne = ligneDe(inscrite.getId());
        assertThat(ligne.abonnementEcheance()).isEqualTo(LocalDate.now().plusYears(1));
        assertThat(ligne.statut()).isEqualTo(StatutAbonnement.ACTIF);
        assertThat(ligne.comptes()).isEqualTo(1);
        assertThat(ligne.ville()).isEqualTo("Douala");
    }

    @Test
    void une_inscription_minimale_suffit() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);

        // Ce que l'editeur sait d'un commercant qu'il vient de demarcher, et rien de plus : le nom
        // de la maison, un numero pour le rappeler, et de quoi ouvrir un compte a son gerant.
        // Lui demander la date de naissance du gerant ou le registre de commerce l'obligeait a
        // les inventer ou a rappeler le client avant de pouvoir creer l'espace.
        InscriptionEntrepriseDto minimale = new InscriptionEntrepriseDto();
        minimale.setEntreprise(EntrepriseDto.builder()
                .nom("Boutique " + suffixe)
                .tel("690000000")
                .build());
        minimale.setAdministrateur(UserDto.builder()
                .nom("Ngono Marie")
                .username("marie-" + suffixe)
                .email("marie-" + suffixe + "@exemple.test")
                .motdepasse(MOT_DE_PASSE)
                .build());

        EntrepriseDto inscrite = entrepriseService.inscrire(minimale);

        assertThat(inscrite.getId()).isNotNull();
        assertThat(ligneDe(inscrite.getId()).statut()).isEqualTo(StatutAbonnement.ACTIF);
        assertThat(utilisateurRepository.findUtilisateurByUsername("marie-" + suffixe))
                .get().extracting(Utilisateur::isMotdepasseAChanger).isEqualTo(true);
    }

    @Test
    void le_mot_de_passe_du_gerant_est_provisoire_et_le_courriel_part() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);

        entrepriseService.inscrire(inscription(suffixe));

        Utilisateur gerant = utilisateurRepository
                .findUtilisateurByUsername("gerant-" + suffixe).orElseThrow();
        assertThat(gerant.isMotdepasseAChanger()).isTrue();

        Envoi courriel = envoiRepository.findAll().stream()
                .filter(e -> ("gerant-" + suffixe + "@exemple.test").equals(e.getDestination()))
                .findFirst().orElseThrow();
        // Marque sensible : la file effacera son corps une fois parti, parce qu'il transporte un
        // mot de passe en clair.
        assertThat(courriel.isSensible()).isTrue();
        assertThat(courriel.getCorps()).contains("gerant-" + suffixe).contains(MOT_DE_PASSE);
    }

    @Test
    void changer_son_mot_de_passe_leve_le_provisoire() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        entrepriseService.inscrire(inscription(suffixe));
        Utilisateur gerant = utilisateurRepository
                .findUtilisateurByUsername("gerant-" + suffixe).orElseThrow();

        connecte(gerant.getEntreprise().getId(), ERole.ROLE_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserDetailsImpl(gerant.getId(), gerant.getUsername(), gerant.getEmail(),
                                "x", gerant.getEntreprise().getId(),
                                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name()))),
                        null, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name()))));

        userService.changerSonMotDePasse(MOT_DE_PASSE, "UnMotDePasseAMoi!2026");

        assertThat(utilisateurRepository.findById(gerant.getId()).orElseThrow()
                .isMotdepasseAChanger()).isFalse();
    }

    @Test
    void suspendre_ferme_la_porte_a_tous_les_comptes_du_commerce() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        EntrepriseDto inscrite = entrepriseService.inscrire(inscription(suffixe));
        assertThat(laPorteEstOuverte("gerant-" + suffixe)).isTrue();

        CommerceDto suspendu = plateformeService.suspendre(inscrite.getId());

        assertThat(suspendu.statut()).isEqualTo(StatutAbonnement.SUSPENDU);
        // Le compte n'a pas ete ferme : c'est le commerce qui l'est, et cela suffit.
        assertThat(utilisateurRepository.findUtilisateurByUsername("gerant-" + suffixe)
                .orElseThrow().isActif()).isTrue();
        assertThat(laPorteEstOuverte("gerant-" + suffixe)).isFalse();
    }

    @Test
    void reprendre_rouvre_la_porte() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        EntrepriseDto inscrite = entrepriseService.inscrire(inscription(suffixe));
        plateformeService.suspendre(inscrite.getId());

        CommerceDto repris = plateformeService.reprendre(inscrite.getId());

        assertThat(repris.statut()).isEqualTo(StatutAbonnement.ACTIF);
        assertThat(laPorteEstOuverte("gerant-" + suffixe)).isTrue();
    }

    @Test
    void une_echeance_passee_ferme_la_porte_d_elle_meme() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        EntrepriseDto inscrite = entrepriseService.inscrire(inscription(suffixe));

        plateformeService.fixerEcheance(inscrite.getId(), LocalDate.now().minusDays(1));

        assertThat(ligneDe(inscrite.getId()).statut()).isEqualTo(StatutAbonnement.ECHU);
        assertThat(laPorteEstOuverte("gerant-" + suffixe)).isFalse();
    }

    @Test
    void l_echeance_vaut_jusqu_a_la_fin_de_son_jour() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        EntrepriseDto inscrite = entrepriseService.inscrire(inscription(suffixe));

        // Echeance aujourd'hui : on ferme demain, pas ce matin.
        plateformeService.fixerEcheance(inscrite.getId(), LocalDate.now());

        assertThat(ligneDe(inscrite.getId()).statut()).isEqualTo(StatutAbonnement.ACTIF);
        assertThat(laPorteEstOuverte("gerant-" + suffixe)).isTrue();
    }

    @Test
    void une_echeance_nulle_laisse_passer() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        EntrepriseDto inscrite = entrepriseService.inscrire(inscription(suffixe));

        // Le cas des entreprises deja en base le jour ou l'abonnement arrive : les bloquer toutes
        // a la seconde serait une panne generale.
        plateformeService.fixerEcheance(inscrite.getId(), null);

        assertThat(ligneDe(inscrite.getId()).statut()).isEqualTo(StatutAbonnement.ACTIF);
        assertThat(laPorteEstOuverte("gerant-" + suffixe)).isTrue();
    }

    @Test
    void renouveler_un_abonnement_en_cours_ajoute_un_an_a_son_echeance() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        EntrepriseDto inscrite = entrepriseService.inscrire(inscription(suffixe));
        LocalDate dans_six_mois = LocalDate.now().plusMonths(6);
        plateformeService.fixerEcheance(inscrite.getId(), dans_six_mois);

        CommerceDto renouvele = plateformeService.renouveler(inscrite.getId());

        // Un renouvellement anticipe ne fait pas perdre les mois deja payes.
        assertThat(renouvele.abonnementEcheance()).isEqualTo(dans_six_mois.plusYears(1));
    }

    @Test
    void renouveler_un_abonnement_echu_repart_d_aujourd_hui() {
        editeur();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        EntrepriseDto inscrite = entrepriseService.inscrire(inscription(suffixe));
        plateformeService.fixerEcheance(inscrite.getId(), LocalDate.now().minusMonths(6));

        CommerceDto renouvele = plateformeService.renouveler(inscrite.getId());

        // Sinon un commerce en retard de six mois paierait pour six mois deja ecoules.
        assertThat(renouvele.abonnementEcheance()).isEqualTo(LocalDate.now().plusYears(1));
        assertThat(renouvele.statut()).isEqualTo(StatutAbonnement.ACTIF);
    }

    @Test
    void un_gerant_ne_voit_pas_la_plateforme() {
        Entreprise sienne = entrepriseRepository.save(EntrepriseDto.toEntity(
                EntrepriseDto.builder().nom("Sienne").registreCommerce("RC-" + UUID.randomUUID())
                        .email("s" + UUID.randomUUID() + "@exemple.test").tel("690000000").build()));
        connecte(sienne.getId(), ERole.ROLE_ADMIN);

        // Le cloisonnement protege chaque commerce des autres ; cette vue les traverse tous.
        assertThatThrownBy(() -> plateformeService.commerces())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> plateformeService.suspendre(sienne.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
