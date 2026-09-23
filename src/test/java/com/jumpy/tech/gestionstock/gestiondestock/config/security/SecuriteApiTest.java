package com.jumpy.tech.gestionstock.gestiondestock.config.security;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les regles d'acces, verifiees route par route.
 *
 * L'API se terminait par `anyRequest().permitAll()` : chacun de ces tests serait passe au vert
 * en renvoyant 200, y compris celui qui attend un refus. C'est la raison d'etre de ce fichier —
 * qu'une prochaine main qui « debloque » une route pour deverminer le fasse en connaissance de
 * cause.
 */
@AutoConfigureMockMvc
class SecuriteApiTest extends AbstractIntegrationTest {

    private static final String API = "/gestiondestock/v1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilisateurRepository utilisateurRepository;

    /**
     * L'inscription est libre tant qu'aucun compte n'existe — c'est l'amorcage. Ce socle garantit
     * donc qu'il en existe un, sans quoi le test du refus verifierait l'exception et non la regle.
     */
    @BeforeEach
    void unCompteExisteDeja() {
        if (utilisateurRepository.count() == 0) {
            utilisateurRepository.save(new Utilisateur("compte-existant", "existant@exemple.test", "peu-importe"));
        }
    }

    @Test
    void sans_jeton_la_lecture_est_refusee() throws Exception {
        mockMvc.perform(get(API + "/articles/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sans_jeton_l_ecriture_est_refusee() throws Exception {
        mockMvc.perform(post(API + "/mouvements/entree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantite\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void un_compte_valide_peut_consulter() throws Exception {
        mockMvc.perform(get(API + "/articles/all"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void le_caissier_ne_touche_pas_au_stock() throws Exception {
        mockMvc.perform(post(API + "/mouvements/entree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantite\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MAGASINIER")
    void le_magasinier_touche_au_stock() throws Exception {
        // Le corps est volontairement incomplet : on verifie que la requete atteint le
        // controleur — un 400 le prouve — et non qu'elle est rejetee en amont par un 403.
        mockMvc.perform(post(API + "/mouvements/entree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantite\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MAGASINIER")
    void seul_l_administrateur_voit_les_comptes() throws Exception {
        mockMvc.perform(get(API + "/users/all"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void le_caissier_ne_voit_pas_les_autres_entreprises() throws Exception {
        mockMvc.perform(get(API + "/entreprises/all"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void le_caissier_lit_l_entreprise_pour_laquelle_il_travaille() throws Exception {
        // Il imprime des tickets a son en-tete toute la journee et etait pourtant le seul a ne
        // pas pouvoir la lire. 404 et non 403 : ce compte de test n'est rattache a aucune
        // entreprise, et c'est bien la route qui a ete atteinte.
        mockMvc.perform(get(API + "/entreprises/mienne"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void le_manager_ne_cree_pas_de_compte() throws Exception {
        // La route reste permitAll au niveau du filtre : c'est le controleur qui arbitre, pour
        // pouvoir laisser passer la toute premiere inscription d'une base vide.
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intrus\",\"email\":\"intrus@exemple.test\","
                                + "\"password\":\"MotDePasse123!\",\"role\":[\"admin\"]}"))
                .andExpect(status().isForbidden());
    }
}
