package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.JwtUtils;
import com.jumpy.tech.gestionstock.gestiondestock.dto.*;
import com.jumpy.tech.gestionstock.gestiondestock.entities.*;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FideliteClientServiceImplTest {

    @Mock
    private CompteClientFideliteRepository clientRepository;
    @Mock
    private SoldePointsMagasinRepository soldeRepository;
    @Mock
    private TicketReclameRepository ticketReclameRepository;
    @Mock
    private BonDAchatRepository bonDAchatRepository;
    @Mock
    private EntrepriseRepository entrepriseRepository;
    @Mock
    private VenteRepository venteRepository;
    @Mock
    private FactureRepository factureRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private CampagneRepository campagneRepository;
    @Mock
    private ReglementRepository reglementRepository;

    private FideliteClientServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FideliteClientServiceImpl(
                clientRepository,
                soldeRepository,
                ticketReclameRepository,
                bonDAchatRepository,
                entrepriseRepository,
                venteRepository,
                factureRepository,
                passwordEncoder,
                jwtUtils,
                campagneRepository,
                Calendrier.fixe(Instant.now(), java.time.ZoneId.of("Africa/Douala")),
                reglementRepository
        );
        // Par defaut, l'achat a eu lieu pendant une campagne encore en cours.
        lenient().when(campagneRepository.ticketScannable(any(), any(), any())).thenReturn(true);
    }

    @Test
    void inscrire_cree_le_compte_et_retourne_jeton() {
        InscriptionClientDto dto = InscriptionClientDto.builder()
                .telephone("690123456")
                .nom("Kamga")
                .prenom("Paul")
                .motDePasse("secret123")
                .build();

        when(clientRepository.existsByTelephone("690123456")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hash_secret");
        when(clientRepository.save(any(CompteClientFidelite.class))).thenAnswer(invocation -> {
            CompteClientFidelite c = invocation.getArgument(0);
            c.setId(10L);
            return c;
        });
        when(jwtUtils.genererJetonPour(any())).thenReturn("mon_jwt_token");

        AuthClientResponseDto reponse = service.inscrire(dto);

        assertThat(reponse.getJeton()).isEqualTo("mon_jwt_token");
        assertThat(reponse.getId()).isEqualTo(10L);
        assertThat(reponse.getTelephone()).isEqualTo("690123456");
        assertThat(reponse.getNom()).isEqualTo("Kamga");
        verify(clientRepository).save(any(CompteClientFidelite.class));
    }

    @Test
    void inscrire_avec_telephone_existant_est_refuse() {
        InscriptionClientDto dto = InscriptionClientDto.builder()
                .telephone("690123456")
                .motDePasse("secret123")
                .build();

        when(clientRepository.existsByTelephone("690123456")).thenReturn(true);

        assertThatThrownBy(() -> service.inscrire(dto))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void reclamer_ticket_succes_credite_les_points() {
        Long idClient = 1L;
        String codeTicket = "7K3M9P2QA4TZ";

        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);
        client.setTelephone("690112233");

        Entreprise entreprise = new Entreprise();
        entreprise.setId(5L);
        entreprise.setNom("Boutique Centrale");
        entreprise.setFideliteActive(true);
        entreprise.setMontantParPoint(new BigDecimal("10000"));
        entreprise.setAbonnementEcheance(LocalDate.now().plusMonths(6));

        Vente vente = new Vente();
        vente.setId(100L);
        vente.setCodeTicket(codeTicket);
        vente.setIdEntreprise(5L);
        vente.setDatevente(Instant.now());

        Facture facture = new Facture();
        facture.setTotalTtc(new BigDecimal("25000")); // 25 000 / 10 000 = 2 points

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(ticketReclameRepository.existsByCodeTicket(codeTicket)).thenReturn(false);
        when(venteRepository.findByCodeTicket(codeTicket)).thenReturn(Optional.of(vente));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.of(entreprise));
        when(factureRepository.findByVenteId(100L)).thenReturn(Optional.of(facture));
        when(soldeRepository.findByClientIdAndEntrepriseId(idClient, 5L)).thenReturn(Optional.empty());
        when(soldeRepository.totalPointsDuClient(idClient)).thenReturn(2);

        ReclamationResultatDto resultat = service.reclamerTicket(idClient, codeTicket);

        assertThat(resultat.getPointsGagnes()).isEqualTo(2);
        assertThat(resultat.getNomMagasin()).isEqualTo("Boutique Centrale");
        assertThat(resultat.getNouveauSoldeMagasin()).isEqualTo(2);
        verify(ticketReclameRepository).saveAndFlush(any(TicketReclame.class));
        verify(soldeRepository).save(any(SoldePointsMagasin.class));
    }

    @Test
    void reclamer_ticket_deja_reclame_est_refuse() {
        Long idClient = 1L;
        String codeTicket = "7K3M9P2QA4TZ";

        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(ticketReclameRepository.existsByCodeTicket(codeTicket)).thenReturn(true);

        assertThatThrownBy(() -> service.reclamerTicket(idClient, codeTicket))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("déjà été enregistré");
    }

    @Test
    void reclamer_ticket_magasin_non_abonne_est_refuse() {
        Long idClient = 1L;
        String codeTicket = "7K3M9P2QA4TZ";

        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);

        Entreprise entreprise = new Entreprise();
        entreprise.setId(5L);
        entreprise.setSuspendue(true); // Fermé / non abonné

        Vente vente = new Vente();
        vente.setId(100L);
        vente.setCodeTicket(codeTicket);
        vente.setIdEntreprise(5L);

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(ticketReclameRepository.existsByCodeTicket(codeTicket)).thenReturn(false);
        when(venteRepository.findByCodeTicket(codeTicket)).thenReturn(Optional.of(vente));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.of(entreprise));

        assertThatThrownBy(() -> service.reclamerTicket(idClient, codeTicket))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("abonnement");
    }

    @Test
    void convertir_points_succes_cree_bon_et_debite_points() {
        Long idClient = 1L;
        Long idEntreprise = 5L;

        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);

        Entreprise entreprise = new Entreprise();
        entreprise.setId(idEntreprise);
        entreprise.setNom("Boutique Centrale");

        SoldePointsMagasin solde = new SoldePointsMagasin();
        solde.setClient(client);
        solde.setEntreprise(entreprise);
        solde.setSoldePoints(1500);

        ConversionPointsDto conversionDto = ConversionPointsDto.builder()
                .idEntreprise(idEntreprise)
                .pointsAConvertir(1000)
                .build();

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(entrepriseRepository.findById(idEntreprise)).thenReturn(Optional.of(entreprise));
        when(soldeRepository.findByClientIdAndEntrepriseId(idClient, idEntreprise)).thenReturn(Optional.of(solde));
        when(bonDAchatRepository.findByCodeBon(anyString())).thenReturn(Optional.empty());
        when(bonDAchatRepository.save(any(BonDAchat.class))).thenAnswer(i -> {
            BonDAchat b = i.getArgument(0);
            b.setId(50L);
            return b;
        });

        BonDAchatDto bon = service.convertirPointsEnBon(idClient, conversionDto);

        assertThat(bon.getMontantFcfa()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(bon.getPointsUtilises()).isEqualTo(1000);
        assertThat(bon.getStatut()).isEqualTo(StatutBonDAchat.ACTIF);
        assertThat(bon.getCodeBon()).startsWith("BON-");
        assertThat(solde.getSoldePoints()).isEqualTo(500); // 1500 - 1000
        verify(soldeRepository).save(solde);
        verify(bonDAchatRepository).save(any(BonDAchat.class));
    }

    @Test
    void convertir_points_solde_insuffisant_est_refuse() {
        Long idClient = 1L;
        Long idEntreprise = 5L;

        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);

        Entreprise entreprise = new Entreprise();
        entreprise.setId(idEntreprise);

        SoldePointsMagasin solde = new SoldePointsMagasin();
        solde.setSoldePoints(400); // seulement 400 points

        ConversionPointsDto conversionDto = ConversionPointsDto.builder()
                .idEntreprise(idEntreprise)
                .pointsAConvertir(1000)
                .build();

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(entrepriseRepository.findById(idEntreprise)).thenReturn(Optional.of(entreprise));
        when(soldeRepository.findByClientIdAndEntrepriseId(idClient, idEntreprise)).thenReturn(Optional.of(solde));

        assertThatThrownBy(() -> service.convertirPointsEnBon(idClient, conversionDto))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Solde insuffisant");
    }

    @Test
    void inscrire_avec_un_identifiant_qui_n_est_pas_un_telephone_est_refuse() {
        InscriptionClientDto dto = InscriptionClientDto.builder()
                .telephone("admin")
                .motDePasse("secret123")
                .build();

        assertThatThrownBy(() -> service.inscrire(dto))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("téléphone invalide");
        verify(clientRepository, never()).save(any());
    }

    @Test
    void le_telephone_est_range_sans_separateurs() {
        assertThat(FideliteClientServiceImpl.telephone(" +237 690-12.34(56) ")).isEqualTo("+237690123456");
    }

    @Test
    void reclamer_un_ticket_pas_encore_facture_le_dit() {
        Long idClient = 1L;
        String codeTicket = "7K3M9P2QA4TZ";
        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);
        Vente vente = venteDe(codeTicket);

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(ticketReclameRepository.existsByCodeTicket(codeTicket)).thenReturn(false);
        when(venteRepository.findByCodeTicket(codeTicket)).thenReturn(Optional.of(vente));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.of(magasinFidele()));
        when(factureRepository.findByVenteId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reclamerTicket(idClient, codeTicket))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("pas encore facturé");
        verify(soldeRepository, never()).save(any());
    }

    @Test
    void deux_clients_qui_scannent_le_meme_ticket_ensemble_un_seul_est_credite() {
        Long idClient = 1L;
        String codeTicket = "7K3M9P2QA4TZ";
        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);
        Facture facture = new Facture();
        facture.setTotalTtc(new BigDecimal("25000"));

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(ticketReclameRepository.existsByCodeTicket(codeTicket)).thenReturn(false);
        when(venteRepository.findByCodeTicket(codeTicket)).thenReturn(Optional.of(venteDe(codeTicket)));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.of(magasinFidele()));
        when(factureRepository.findByVenteId(100L)).thenReturn(Optional.of(facture));
        // L'autre client est passe entre le controle et l'ecriture : la contrainte d'unicite tranche.
        when(ticketReclameRepository.saveAndFlush(any(TicketReclame.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq_ticket_reclame_code"));

        assertThatThrownBy(() -> service.reclamerTicket(idClient, codeTicket))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("déjà été enregistré");
        verify(soldeRepository, never()).save(any());
    }

    @Test
    void la_conversion_suit_la_politique_du_magasin() {
        Long idClient = 1L;
        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);
        Entreprise entreprise = magasinFidele();
        entreprise.setValeurPointFcfa(new BigDecimal("2.5"));
        entreprise.setPointsMinimumBon(500);
        entreprise.setDureeValiditeBonJours(30);
        SoldePointsMagasin solde = new SoldePointsMagasin();
        solde.setSoldePoints(1000);

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.of(entreprise));
        when(soldeRepository.findByClientIdAndEntrepriseId(idClient, 5L)).thenReturn(Optional.of(solde));
        when(bonDAchatRepository.findByCodeBon(anyString())).thenReturn(Optional.empty());
        when(bonDAchatRepository.save(any(BonDAchat.class))).thenAnswer(i -> i.getArgument(0));

        BonDAchatDto bon = service.convertirPointsEnBon(idClient,
                ConversionPointsDto.builder().idEntreprise(5L).pointsAConvertir(601).build());

        // 601 points a 2,50 F : 1502,50 F, arrondis au franc inferieur.
        assertThat(bon.getMontantFcfa()).isEqualByComparingTo("1502");
        assertThat(ChronoUnit.DAYS.between(bon.getDateEmission(), bon.getDateExpiration())).isEqualTo(30);
        assertThat(solde.getSoldePoints()).isEqualTo(399);
    }

    @Test
    void sous_le_minimum_du_magasin_la_conversion_est_refusee() {
        Long idClient = 1L;
        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);
        Entreprise entreprise = magasinFidele();
        entreprise.setPointsMinimumBon(1000);
        SoldePointsMagasin solde = new SoldePointsMagasin();
        solde.setSoldePoints(5000);

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.of(entreprise));
        when(soldeRepository.findByClientIdAndEntrepriseId(idClient, 5L)).thenReturn(Optional.of(solde));

        assertThatThrownBy(() -> service.convertirPointsEnBon(idClient,
                ConversionPointsDto.builder().idEntreprise(5L).pointsAConvertir(999).build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("au moins 1000 points");
        assertThat(solde.getSoldePoints()).isEqualTo(5000);
        verify(bonDAchatRepository, never()).save(any());
    }

    @Test
    void un_achat_hors_campagne_ne_rapporte_rien() {
        Long idClient = 1L;
        String codeTicket = "7K3M9P2QA4TZ";
        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(ticketReclameRepository.existsByCodeTicket(codeTicket)).thenReturn(false);
        when(venteRepository.findByCodeTicket(codeTicket)).thenReturn(Optional.of(venteDe(codeTicket)));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.of(magasinFidele()));
        when(campagneRepository.ticketScannable(any(), any(), any())).thenReturn(false);
        when(campagneRepository.achatEnCampagne(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.reclamerTicket(idClient, codeTicket))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("pendant une campagne");
        verify(soldeRepository, never()).save(any());
    }

    @Test
    void apres_la_fin_de_sa_campagne_un_ticket_ne_se_scanne_plus() {
        Long idClient = 1L;
        String codeTicket = "7K3M9P2QA4TZ";
        CompteClientFidelite client = new CompteClientFidelite();
        client.setId(idClient);

        when(clientRepository.verrouiller(idClient)).thenReturn(Optional.of(client));
        when(ticketReclameRepository.existsByCodeTicket(codeTicket)).thenReturn(false);
        when(venteRepository.findByCodeTicket(codeTicket)).thenReturn(Optional.of(venteDe(codeTicket)));
        when(entrepriseRepository.findById(5L)).thenReturn(Optional.of(magasinFidele()));
        when(campagneRepository.ticketScannable(any(), any(), any())).thenReturn(false);
        when(campagneRepository.achatEnCampagne(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.reclamerTicket(idClient, codeTicket))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("campagne de cet achat est terminée");
    }

    private static Vente venteDe(String codeTicket) {
        Vente vente = new Vente();
        vente.setId(100L);
        vente.setCodeTicket(codeTicket);
        vente.setIdEntreprise(5L);
        vente.setDatevente(Instant.now());
        return vente;
    }

    private static Entreprise magasinFidele() {
        Entreprise entreprise = new Entreprise();
        entreprise.setId(5L);
        entreprise.setNom("Boutique Centrale");
        entreprise.setFideliteActive(true);
        entreprise.setMontantParPoint(new BigDecimal("10000"));
        entreprise.setAbonnementEcheance(LocalDate.now().plusMonths(6));
        return entreprise;
    }
}
