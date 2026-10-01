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

import org.springframework.test.web.servlet.ResultMatcher;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    // --- Les alertes sur l'appareil -----------------------------------------------------------
    // Chacun abonne et desabonne son propre appareil. Les regles generiques reservaient l'un a
    // trois roles et l'autre a deux : le magasinier qui quittait une caisse partagee ne pouvait
    // pas en retirer l'appareil, qui continuait de recevoir ses alertes. Ce que ces tests
    // verifient, c'est que la requete atteint le controleur — tout sauf un 403.

    @Test
    @WithMockUser(roles = "CAISSIER")
    void le_caissier_peut_abonner_son_appareil() throws Exception {
        mockMvc.perform(post(API + "/notifications/push/abonnements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\"}"))
                .andExpect(pasRefuse());
    }

    @Test
    @WithMockUser(roles = "MAGASINIER")
    void le_magasinier_peut_desabonner_son_appareil() throws Exception {
        mockMvc.perform(delete(API + "/notifications/push/abonnements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\"}"))
                .andExpect(pasRefuse());
    }

    @Test
    @WithMockUser(roles = "COMPTABLE")
    void le_comptable_peut_desabonner_son_appareil() throws Exception {
        mockMvc.perform(delete(API + "/notifications/push/abonnements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\"}"))
                .andExpect(pasRefuse());
    }

    // --- Le ticket d'un client ----------------------------------------------------------------

    @Test
    void le_ticket_se_consulte_sans_compte() throws Exception {
        // Le client qui scanne son ticket n'a pas de compte. Un code inconnu rend 404 — le
        // controleur a repondu —, et non 401.
        mockMvc.perform(get(API + "/tickets/ZZZZZZZZZZZZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void le_reste_de_l_api_reste_ferme_sans_compte() throws Exception {
        // L'ouverture du ticket ne doit pas s'etendre a ce qui l'entoure.
        mockMvc.perform(get(API + "/tickets"))
                .andExpect(status().isUnauthorized());
    }

    // --- Fidelite client mobile & bons d'achat ------------------------------------------------

    @Test
    void les_magasins_en_promotion_sont_accessibles_sans_compte() throws Exception {
        mockMvc.perform(get(API + "/fidelite/magasins"))
                .andExpect(status().isOk());
    }

    @Test
    void le_profil_fidelite_exige_un_compte() throws Exception {
        mockMvc.perform(get(API + "/fidelite/profil"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void l_espace_client_n_est_pas_ouvert_au_personnel() throws Exception {
        // Ces routes lisent l'id du compte connecte comme un id de client : un employe y lirait
        // le client qui porte le meme numero que lui.
        mockMvc.perform(get(API + "/fidelite/profil"))
                .andExpect(status().isForbidden());
    }

    @Test
    void le_client_mobile_ne_lit_pas_la_gestion() throws Exception {
        // L'inscription est libre : sans cette regle, n'importe qui obtenait un jeton ouvrant
        // toutes les lectures « tout compte connecte » du back-office.
        String jeton = inscrireClient(telephoneNeuf());

        mockMvc.perform(get(API + "/articles/all").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(API + "/users/moi").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(API + "/fidelite/profil").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk());
    }

    @Test
    void un_client_ne_prend_pas_le_compte_d_un_employe_au_meme_nom() throws Exception {
        // Un employe dont l'identifiant a la forme d'un numero de telephone. Le jeton du client
        // inscrit avec ce numero portait ce nom, et se relisait d'abord parmi le personnel.
        String numero = telephoneNeuf();
        utilisateurRepository.save(new Utilisateur(numero, numero + "@exemple.test", "peu-importe"));

        String jeton = inscrireClient(numero);

        mockMvc.perform(get(API + "/users/moi").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_identifiant_qui_n_est_pas_un_telephone_est_refuse() throws Exception {
        mockMvc.perform(post(API + "/fidelite/auth/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telephone\":\"compte-existant\",\"motDePasse\":\"secret123\"}"))
                .andExpect(status().isBadRequest());
    }

    private String inscrireClient(String telephone) throws Exception {
        String reponse = mockMvc.perform(post(API + "/fidelite/auth/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telephone\":\"" + telephone + "\",\"motDePasse\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(reponse, "$.jeton");
    }

    /** Les tests partagent une base : chaque inscription prend un numero qu'aucune autre n'a pris. */
    private static String telephoneNeuf() {
        return "6" + String.format("%08d", (System.nanoTime() / 1000) % 100_000_000L);
    }

    private static ResultMatcher pasRefuse() {
        return resultat -> org.assertj.core.api.Assertions
                .assertThat(resultat.getResponse().getStatus())
                .as("la requete doit atteindre le controleur")
                .isNotIn(401, 403);
    }
}
