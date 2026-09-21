package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * L'unicite des comptes appartient a la base.
 *
 * AuthControler verifiait deja l'absence de doublon avant d'inserer, mais en Java : entre le
 * `existsByUsername` et le `save`, une seconde requete passe et les deux comptes sont ecrits.
 * Ce test ecrit directement par le repository, en contournant le controleur, pour verifier que la
 * base refuse elle-meme — c'est la seule garantie qui tienne sous deux requetes simultanees.
 */
class UniciteDesComptesTest extends AbstractIntegrationTest {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Test
    void deux_comptes_ne_peuvent_pas_partager_le_meme_identifiant() {
        String username = "doublon-" + UUID.randomUUID();
        utilisateurRepository.saveAndFlush(new Utilisateur(username, "premier-" + UUID.randomUUID() + "@x.test", "x"));

        assertThatThrownBy(() -> utilisateurRepository.saveAndFlush(
                new Utilisateur(username, "second-" + UUID.randomUUID() + "@x.test", "x")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deux_comptes_ne_peuvent_pas_partager_le_meme_courriel() {
        String email = "doublon-" + UUID.randomUUID() + "@exemple.test";
        utilisateurRepository.saveAndFlush(new Utilisateur("premier-" + UUID.randomUUID(), email, "x"));

        assertThatThrownBy(() -> utilisateurRepository.saveAndFlush(
                new Utilisateur("second-" + UUID.randomUUID(), email, "x")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void plusieurs_comptes_sans_courriel_restent_possibles() {
        // PostgreSQL admet plusieurs NULL dans un index unique. Un compte de service cree sans
        // adresse ne doit pas bloquer le suivant.
        utilisateurRepository.saveAndFlush(new Utilisateur("sans-courriel-" + UUID.randomUUID(), null, "x"));

        assertThatCode(() -> utilisateurRepository.saveAndFlush(
                new Utilisateur("sans-courriel-" + UUID.randomUUID(), null, "x")))
                .doesNotThrowAnyException();
    }
}
