package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.ServiceDeRafraichissement;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AdresseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.repository.JetonRafraichissementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le jeton de rafraichissement.
 *
 * Un JWT ne se revoque pas : signe, il vaut jusqu'a son expiration, et fermer un compte ne le
 * rappelle pas. Le jeton d'acces durait 24 h faute de pouvoir le renouveler — un employe renvoye
 * gardait ses acces jusqu'au lendemain.
 */
class JetonDeRafraichissementTest extends AbstractIntegrationTest {

    @Autowired
    private ServiceDeRafraichissement rafraichissement;
    @Autowired
    private UserService userService;
    @Autowired
    private JetonRafraichissementRepository repository;

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private UserDto unCompte() {
        return userService.save(UserDto.builder()
                .nom("Employé")
                .prenoms("Test")
                .username("u-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@exemple.test")
                .motdepasse("MotDePasse123!")
                .numTel("690000000")
                .dateNaissance(Instant.parse("1990-01-01T00:00:00Z"))
                .adresse(AdresseDto.builder().adresse1("Rue 1").ville("Douala").pays("Cameroun").build())
                .build());
    }

    private void connecte(UserDto compte) {
        UserDetailsImpl principal = new UserDetailsImpl(compte.getId(), compte.getUsername(),
                compte.getEmail(), "x", null,
                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void un_echange_rend_un_jeton_neuf_et_retire_l_ancien() {
        UserDto compte = unCompte();
        String premier = rafraichissement.creer(compte.getId());

        ServiceDeRafraichissement.Rafraichi apres = rafraichissement.echanger(premier);

        assertThat(apres.jeton()).isNotEqualTo(premier);
        assertThat(apres.details().getUsername()).isEqualTo(compte.getUsername());
        // L'ancien est revoque, pas supprime : le representer doit pouvoir se remarquer.
        assertThat(repository.findByJeton(premier)).get()
                .extracting(j -> j.getRevoqueLe() != null).isEqualTo(true);
    }

    @Test
    void un_jeton_deja_echange_ferme_tout_le_compte() {
        UserDto compte = unCompte();
        String premier = rafraichissement.creer(compte.getId());
        String second = rafraichissement.echanger(premier).jeton();

        // Un jeton remplace par rotation ne devait jamais revenir : le client qui l'a echange en a
        // recu un autre. S'il revient, c'est qu'une copie circule.
        assertThatThrownBy(() -> rafraichissement.echanger(premier))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("déjà utilisé");

        // Le voleur et le titulaire se reconnecteront ; le titulaire s'en apercevra.
        assertThatThrownBy(() -> rafraichissement.echanger(second))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejouer_un_jeton_deconnecte_ne_ferme_pas_les_autres_appareils() {
        UserDto compte = unCompte();
        String telephone = rafraichissement.creer(compte.getId());
        String comptoir = rafraichissement.creer(compte.getId());
        rafraichissement.revoquer(telephone);

        // Un onglet reste ouvert, une requete differee qui repart : c'est une maladresse de
        // client, pas un vol. Fermer tout le compte pour cela couperait la caisse du comptoir.
        assertThatThrownBy(() -> rafraichissement.echanger(telephone))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(rafraichissement.echanger(comptoir).jeton()).isNotNull();
    }

    @Test
    void un_jeton_inconnu_est_refuse() {
        assertThatThrownBy(() -> rafraichissement.echanger("jeton-qui-n-existe-pas"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("inconnu");
    }

    @Test
    void une_deconnexion_ne_ferme_que_l_appareil_qui_la_demande() {
        UserDto compte = unCompte();
        String telephone = rafraichissement.creer(compte.getId());
        String comptoir = rafraichissement.creer(compte.getId());

        rafraichissement.revoquer(telephone);

        // Se deconnecter de son telephone ne doit pas fermer la caisse restee ouverte.
        assertThat(rafraichissement.echanger(comptoir).jeton()).isNotNull();
        assertThatThrownBy(() -> rafraichissement.echanger(telephone))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void fermer_un_acces_ferme_aussi_ses_jetons() {
        UserDto compte = unCompte();
        String jeton = rafraichissement.creer(compte.getId());
        connecte(unCompte());

        userService.changerActivation(compte.getId(), false);

        // Sans cela, fermer le compte n'aurait ferme que la porte d'entree.
        assertThatThrownBy(() -> rafraichissement.echanger(jeton))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void un_compte_ferme_ne_redemande_pas_de_jeton() {
        UserDto compte = unCompte();
        connecte(unCompte());
        userService.changerActivation(compte.getId(), false);
        // Un jeton emis apres coup ne doit pas davantage servir : c'est l'etat du compte qui
        // decide, pas la date du jeton.
        String jeton = rafraichissement.creer(compte.getId());

        assertThatThrownBy(() -> rafraichissement.echanger(jeton))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("fermé");
    }

    @Test
    void reinitialiser_un_mot_de_passe_ferme_les_sessions_ouvertes() {
        UserDto compte = unCompte();
        String jeton = rafraichissement.creer(compte.getId());
        connecte(unCompte());

        userService.reinitialiserMotDePasse(compte.getId(), "NouveauMotDePasse123!");

        // Un mot de passe qu'on reinitialise est un mot de passe qu'on soupconne.
        assertThatThrownBy(() -> rafraichissement.echanger(jeton))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void changer_son_mot_de_passe_ferme_ses_autres_sessions() {
        UserDto compte = unCompte();
        String jeton = rafraichissement.creer(compte.getId());
        connecte(compte);

        userService.changerSonMotDePasse("MotDePasse123!", "NouveauMotDePasse123!");

        assertThatThrownBy(() -> rafraichissement.echanger(jeton))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void deux_jetons_ne_se_ressemblent_jamais() {
        UserDto compte = unCompte();

        // Tires au hasard sur 256 bits : deux jetons identiques signifieraient qu'un compte peut
        // en ouvrir un autre.
        assertThat(rafraichissement.creer(compte.getId()))
                .isNotEqualTo(rafraichissement.creer(compte.getId()));
    }
}
