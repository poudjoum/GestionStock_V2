package com.jumpy.tech.gestionstock.gestiondestock.config.security;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AdresseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce qui manquait pour qu'un front puisse exister.
 *
 * Le CORS n'etait pose que sur la route de connexion : le front obtenait son jeton, puis le
 * navigateur lui refusait toutes les autres requetes, sans que rien cote serveur ne le signale.
 * Et rien ne permettait de redemander qui porte un jeton.
 */
@AutoConfigureMockMvc
class OuvertureAuFrontTest extends AbstractIntegrationTest {

    private static final String API = "/gestiondestock/v1";
    private static final String FRONT = "http://localhost:4200";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;

    // --- CORS ---------------------------------------------------------------------------------

    @Test
    void la_requete_preliminaire_du_front_est_acceptee_sans_jeton() throws Exception {
        // Une requete preliminaire ne porte pas de jeton : refusee avant d'etre traitee, elle
        // bloquerait toutes les requetes qui la suivent.
        mockMvc.perform(options(API + "/articles")
                        .header(HttpHeaders.ORIGIN, FRONT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT));
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void une_lecture_depuis_le_front_porte_l_en_tete_qui_l_autorise() throws Exception {
        mockMvc.perform(get(API + "/articles/all").header(HttpHeaders.ORIGIN, FRONT))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT));
    }

    @Test
    void une_origine_non_declaree_est_refusee() throws Exception {
        // L'etoile ouvrait l'API a n'importe quelle page du web : nommer les origines coute une
        // variable d'environnement.
        mockMvc.perform(options(API + "/articles")
                        .header(HttpHeaders.ORIGIN, "https://site-inconnu.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void la_connexion_n_est_plus_la_seule_route_ouverte_au_front() throws Exception {
        // Le @CrossOrigin(origins="*") pose sur /api/auth n'ouvrait que la connexion.
        mockMvc.perform(options("/api/auth/signin")
                        .header(HttpHeaders.ORIGIN, FRONT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT));
    }

    // --- Qui suis-je --------------------------------------------------------------------------

    @Test
    void sans_jeton_on_ne_demande_pas_qui_on_est() throws Exception {
        mockMvc.perform(get(API + "/users/moi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tout_compte_connecte_lit_son_propre_compte() throws Exception {
        UserDto compte = unCompte();

        // Le reste de /users est reserve a l'administration ; celle-ci ne l'est pas — on ne
        // demande pas a quelqu'un s'il a le droit de savoir qui il est.
        mockMvc.perform(get(API + "/users/moi").with(user(compte.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(compte.getUsername()))
                .andExpect(jsonPath("$.email").value(compte.getEmail()));
    }

    @Test
    void le_compte_rendu_ne_porte_jamais_son_mot_de_passe() throws Exception {
        UserDto compte = unCompte();

        mockMvc.perform(get(API + "/users/moi").with(user(compte.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motdepasse").doesNotExist());
    }

    @Test
    @WithMockUser(username = "inconnu-au-bataillon", roles = "CAISSIER")
    void un_jeton_dont_le_compte_a_disparu_ne_rend_personne() throws Exception {
        mockMvc.perform(get(API + "/users/moi"))
                .andExpect(status().isNotFound());
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor user(String username) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(username).roles("CAISSIER");
    }
}
