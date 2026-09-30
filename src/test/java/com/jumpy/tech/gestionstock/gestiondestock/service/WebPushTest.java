package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AbonnementPushDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AdresseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CanalEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeNotification;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.AbonnementPushRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EnvoiRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.Impl.Expediteur;
import com.jumpy.tech.gestionstock.gestiondestock.webpush.CourbeP256;
import com.jumpy.tech.gestionstock.gestiondestock.webpush.PortePush;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Web Push : une alerte ecrite dans l'application arrive, chiffree, sur l'appareil abonne.
 *
 * Le service push est remplace par un faux qui garde ce qu'on lui depose. Le test joue le
 * navigateur : il tire sa propre paire de cles, s'abonne, puis dechiffre ce que le serveur a
 * depose. C'est la seule preuve que le message arrivera lisible — le service push, lui, accepte
 * n'importe quel corps sans pouvoir le lire.
 */
class WebPushTest extends AbstractIntegrationTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final KeyPair VAPID = CourbeP256.nouvellePaire();

    @DynamicPropertySource
    static void clesVapid(DynamicPropertyRegistry proprietes) {
        proprietes.add("app.push.clePublique", () -> B64.encodeToString(CourbeP256.brute(VAPID.getPublic())));
        proprietes.add("app.push.clePrivee", () -> B64.encodeToString(CourbeP256.brute(VAPID.getPrivate())));
        proprietes.add("app.push.sujet", () -> "mailto:exploitant@exemple.test");
    }

    @MockitoBean
    private PortePush porte;

    @Autowired
    private NotificationService notifications;
    @Autowired
    private UserService userService;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private AbonnementPushRepository abonnements;
    @Autowired
    private EnvoiRepository envoiRepository;
    @Autowired
    private Expediteur expediteur;
    @Autowired
    private UtilisateurRepository utilisateurs;

    private Long idEntreprise;
    private String magasinier;

    /** Le navigateur du test : sa paire de cles et son secret, comme un vrai en tire a l'abonnement. */
    private final KeyPair navigateur = CourbeP256.nouvellePaire();
    private final byte[] secretAuth = UUID.randomUUID().toString().substring(0, 16).getBytes(StandardCharsets.US_ASCII);
    private String adresse;

    @BeforeEach
    void unMagasinierEtSonTelephone() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Quincaillerie " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        connecte("amorceur", null, ERole.ROLE_SUPER_ADMIN);
        magasinier = creerCompte(ERole.ROLE_MAGASINIER);
        adresse = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
        reset(porte);
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

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

    private AbonnementPushDto abonnement(String adresse) {
        return AbonnementPushDto.builder()
                .endpoint(adresse)
                .keys(AbonnementPushDto.Cles.builder()
                        .p256dh(B64.encodeToString(CourbeP256.brute(navigateur.getPublic())))
                        .auth(B64.encodeToString(secretAuth))
                        .build())
                .build();
    }

    private void leMagasinierAbonneSonTelephone() {
        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);
        notifications.abonnerCetAppareil(abonnement(adresse), "Chrome sur Android");
    }

    private void uneRupture() {
        notifications.prevenirLesRoles(idEntreprise, List.of(ERole.ROLE_MAGASINIER),
                TypeNotification.STOCK_ALERTE, "Rupture : Sac de ciment",
                "Il n'en reste plus.", "/stock", "rupture-" + UUID.randomUUID());
    }

    private List<Envoi> envoisPush() {
        return envoiRepository.findAll().stream()
                .filter(e -> e.getCanal() == CanalEnvoi.PUSH)
                .filter(e -> abonnements.findByAdresse(adresse)
                        .map(a -> String.valueOf(a.getId()).equals(e.getDestination()))
                        .orElse(false))
                .toList();
    }

    // --- L'abonnement -------------------------------------------------------------------------

    @Test
    void la_cle_publique_est_celle_du_serveur() {
        assertThat(notifications.clePush())
                .contains(B64.encodeToString(CourbeP256.brute(VAPID.getPublic())));
    }

    @Test
    void un_appareil_s_abonne_au_compte_connecte() {
        leMagasinierAbonneSonTelephone();

        var enregistre = abonnements.findByAdresse(adresse).orElseThrow();
        assertThat(enregistre.getAppareil()).isEqualTo("Chrome sur Android");
        assertThat(enregistre.getIdEntreprise()).isEqualTo(idEntreprise);
    }

    @Test
    void un_appareil_partage_passe_au_dernier_compte_connecte() {
        leMagasinierAbonneSonTelephone();
        String collegue = creerCompte(ERole.ROLE_MAGASINIER);

        connecte(collegue, idEntreprise, ERole.ROLE_MAGASINIER);
        notifications.abonnerCetAppareil(abonnement(adresse), "Chrome sur Android");

        // Une caisse partagee : celui qui est parti ne doit plus y recevoir ses alertes.
        assertThat(abonnements.findAll().stream().filter(a -> a.getAdresse().equals(adresse))).hasSize(1);
        // L'identifiant, et non le nom : lire le nom chargerait le compte hors transaction.
        assertThat(abonnements.findByAdresse(adresse).orElseThrow().getUtilisateur().getId())
                .isEqualTo(utilisateurs.findUtilisateurByUsername(collegue).orElseThrow().getId());
    }

    @Test
    void une_adresse_qui_n_est_pas_un_service_push_est_refusee() {
        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);

        // Sans ce refus, ce serveur irait deposer des messages sur son propre reseau.
        for (String adresseInterne : List.of(
                "http://192.168.1.1/admin",
                "https://192.168.1.100:9092/gestiondestock/v1/users",
                "https://fcm.googleapis.com.exemple.test/x",
                "http://fcm.googleapis.com/fcm/send/x",
                "https://fcm.googleapis.com:8443/fcm/send/x")) {
            assertThatThrownBy(() -> notifications.abonnerCetAppareil(abonnement(adresseInterne), null))
                    .as(adresseInterne)
                    .isInstanceOf(InvalidEntityException.class);
        }
    }

    @Test
    void des_cles_mal_formees_sont_refusees() {
        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);
        AbonnementPushDto abime = abonnement(adresse);
        abime.getKeys().setAuth("trop-court");

        assertThatThrownBy(() -> notifications.abonnerCetAppareil(abime, null))
                .isInstanceOf(InvalidEntityException.class);
    }

    @Test
    void on_ne_desabonne_pas_l_appareil_d_un_autre() {
        leMagasinierAbonneSonTelephone();
        String autre = creerCompte(ERole.ROLE_CAISSIER);

        connecte(autre, idEntreprise, ERole.ROLE_CAISSIER);
        notifications.desabonnerCetAppareil(adresse);

        assertThat(abonnements.findByAdresse(adresse)).isPresent();

        connecte(magasinier, idEntreprise, ERole.ROLE_MAGASINIER);
        notifications.desabonnerCetAppareil(adresse);

        assertThat(abonnements.findByAdresse(adresse)).isEmpty();
    }

    // --- L'envoi ------------------------------------------------------------------------------

    @Test
    void une_alerte_arrive_chiffree_et_lisible_sur_l_appareil() throws Exception {
        leMagasinierAbonneSonTelephone();
        when(porte.deposer(any(), any(), any())).thenReturn(201);

        uneRupture();
        expediteur.traiterLesPush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> entetes = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<byte[]> corps = ArgumentCaptor.forClass(byte[].class);
        verify(porte).deposer(org.mockito.ArgumentMatchers.eq(URI.create(adresse)),
                entetes.capture(), corps.capture());

        assertThat(entetes.getValue())
                .containsEntry("Content-Encoding", "aes128gcm")
                .containsKey("TTL");
        assertThat(entetes.getValue().get("Authorization")).startsWith("vapid t=");

        // Le navigateur dechiffre ce qu'il recoit : c'est le message que le service worker affichera.
        String message = dechiffrer(corps.getValue());
        assertThat(message)
                .contains("\"title\":\"Rupture : Sac de ciment\"")
                .contains("\"body\":\"Il n'en reste plus.\"")
                .contains("\"url\":\"/stock\"")
                .contains("navigateLastFocusedOrOpen");
        assertThat(envoisPush()).extracting(Envoi::getEtat).containsOnly(EtatEnvoi.ENVOYE);
    }

    @Test
    void sans_appareil_abonne_rien_n_est_mis_en_file() {
        long pushAvant = envoiRepository.findAll().stream()
                .filter(e -> e.getCanal() == CanalEnvoi.PUSH).count();

        uneRupture();

        // La notification est ecrite pour le magasinier ; aucun envoi push, il n'a pas d'appareil.
        assertThat(envoiRepository.findAll().stream()
                .filter(e -> e.getCanal() == CanalEnvoi.PUSH).count())
                .isEqualTo(pushAvant);
        verify(porte, never()).deposer(any(), any(), any());
    }

    @Test
    void un_appareil_desabonne_est_oublie_et_l_envoi_abandonne_sans_reessayer() {
        leMagasinierAbonneSonTelephone();
        when(porte.deposer(any(), any(), any())).thenReturn(410);
        uneRupture();
        List<Envoi> envois = envoisPush();

        expediteur.traiterLesPush();

        // 410 : l'appareil ne recevra plus rien, au premier essai comme au sixieme.
        Envoi relu = envoiRepository.findById(envois.get(0).getId()).orElseThrow();
        assertThat(relu.getEtat()).isEqualTo(EtatEnvoi.ABANDONNE);
        assertThat(relu.getTentatives()).isEqualTo(1);
        assertThat(abonnements.findByAdresse(adresse)).isEmpty();
    }

    @Test
    void un_service_push_en_panne_est_retente_plus_tard() {
        leMagasinierAbonneSonTelephone();
        when(porte.deposer(any(), any(), any())).thenReturn(503);
        uneRupture();
        List<Envoi> envois = envoisPush();

        expediteur.traiterLesPush();

        Envoi relu = envoiRepository.findById(envois.get(0).getId()).orElseThrow();
        assertThat(relu.getEtat()).isEqualTo(EtatEnvoi.A_ENVOYER);
        assertThat(relu.getProchaineTentative()).isAfter(Instant.now());
        // L'abonnement reste : c'est le service qui va mal, pas l'appareil.
        assertThat(abonnements.findByAdresse(adresse)).isPresent();
    }

    /**
     * Ce que fait le navigateur a reception (RFC 8291, dans l'autre sens) : retrouver le secret
     * partage avec sa cle privee et la cle ephemere du serveur, et dechiffrer.
     */
    private String dechiffrer(byte[] corps) throws Exception {
        ByteBuffer lecture = ByteBuffer.wrap(corps);
        byte[] sel = new byte[16];
        lecture.get(sel);
        lecture.getInt();
        byte[] cleServeur = new byte[lecture.get()];
        lecture.get(cleServeur);
        byte[] chiffre = new byte[lecture.remaining()];
        lecture.get(chiffre);

        KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
        ecdh.init(navigateur.getPrivate());
        ecdh.doPhase(CourbeP256.clePublique(cleServeur), true);
        byte[] infoCle = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0},
                CourbeP256.brute(navigateur.getPublic()), cleServeur);
        byte[] ikm = hkdf(secretAuth, ecdh.generateSecret(), infoCle, 32);
        byte[] cle = hkdf(sel, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 16);
        byte[] nonce = hkdf(sel, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cle, "AES"), new GCMParameterSpec(128, nonce));
        byte[] clair = aes.doFinal(chiffre);
        // Le dernier octet est le delimiteur d'enregistrement, 0x02.
        assertThat(clair[clair.length - 1]).isEqualTo((byte) 2);
        return new String(Arrays.copyOf(clair, clair.length - 1), StandardCharsets.UTF_8);
    }

    private static byte[] hkdf(byte[] sel, byte[] ikm, byte[] info, int longueur) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(sel, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(concat(info, new byte[]{1})), longueur);
    }

    private static byte[] concat(byte[]... morceaux) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        for (byte[] m : morceaux) {
            sortie.writeBytes(m);
        }
        return sortie.toByteArray();
    }
}
