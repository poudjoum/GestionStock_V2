package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.JwtUtils;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.*;
import com.jumpy.tech.gestionstock.gestiondestock.entities.*;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FideliteClientServiceImpl implements FideliteClientService {

    private static final Pattern TELEPHONE = Pattern.compile("\\+?[0-9]{8,15}");
    private static final int MOT_DE_PASSE_MIN = 6;

    private final CompteClientFideliteRepository clientRepository;
    private final SoldePointsMagasinRepository soldeRepository;
    private final TicketReclameRepository ticketReclameRepository;
    private final BonDAchatRepository bonDAchatRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final VenteRepository venteRepository;
    private final FactureRepository factureRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public FideliteClientServiceImpl(CompteClientFideliteRepository clientRepository,
                                     SoldePointsMagasinRepository soldeRepository,
                                     TicketReclameRepository ticketReclameRepository,
                                     BonDAchatRepository bonDAchatRepository,
                                     EntrepriseRepository entrepriseRepository,
                                     VenteRepository venteRepository,
                                     FactureRepository factureRepository,
                                     PasswordEncoder passwordEncoder,
                                     JwtUtils jwtUtils) {
        this.clientRepository = clientRepository;
        this.soldeRepository = soldeRepository;
        this.ticketReclameRepository = ticketReclameRepository;
        this.bonDAchatRepository = bonDAchatRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.venteRepository = venteRepository;
        this.factureRepository = factureRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Le numero tel qu'il est range : sans espaces, tirets, points ni parentheses, un `+` initial
     * conserve. Huit a quinze chiffres, la longueur d'un numero E.164.
     *
     * Le controle n'est pas qu'une politesse : l'identifiant d'un compte fidelite ne doit pas
     * pouvoir prendre n'importe quelle forme, et surtout pas celle d'un nom de compte du personnel.
     */
    static String telephone(String saisi) {
        String numero = saisi == null ? "" : saisi.trim().replaceAll("[\\s.()-]", "");
        if (!TELEPHONE.matcher(numero).matches()) {
            throw new InvalidEntityException("Numéro de téléphone invalide : 8 à 15 chiffres attendus.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Téléphone invalide"));
        }
        return numero;
    }

    @Override
    @Transactional
    public AuthClientResponseDto inscrire(InscriptionClientDto dto) {
        if (dto == null || !StringUtils.hasText(dto.getTelephone()) || !StringUtils.hasText(dto.getMotDePasse())) {
            throw new InvalidEntityException("Le numéro de téléphone et le mot de passe sont obligatoires.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Téléphone et mot de passe requis"));
        }

        String telephone = telephone(dto.getTelephone());
        if (dto.getMotDePasse().length() < MOT_DE_PASSE_MIN) {
            throw new InvalidEntityException("Le mot de passe doit compter au moins " + MOT_DE_PASSE_MIN + " caractères.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Mot de passe trop court"));
        }
        if (clientRepository.existsByTelephone(telephone)) {
            throw new InvalidEntityException("Un compte existe déjà avec ce numéro de téléphone : " + telephone,
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Numéro déjà utilisé"));
        }

        CompteClientFidelite client = new CompteClientFidelite();
        client.setTelephone(telephone);
        client.setNom(dto.getNom() != null ? dto.getNom().trim() : null);
        client.setPrenom(dto.getPrenom() != null ? dto.getPrenom().trim() : null);
        client.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
        client.setActif(true);

        CompteClientFidelite enregistre = clientRepository.save(client);
        String jeton = jwtUtils.genererJetonPour(UserDetailsImpl.buildClient(enregistre));

        return AuthClientResponseDto.builder()
                .jeton(jeton)
                .id(enregistre.getId())
                .telephone(enregistre.getTelephone())
                .nom(enregistre.getNom())
                .prenom(enregistre.getPrenom())
                .totalPoints(0)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthClientResponseDto connecter(ConnexionClientDto dto) {
        if (dto == null || !StringUtils.hasText(dto.getTelephone()) || !StringUtils.hasText(dto.getMotDePasse())) {
            throw new InvalidEntityException("Le numéro de téléphone et le mot de passe sont obligatoires.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Identifiants manquants"));
        }

        String telephone = telephone(dto.getTelephone());
        CompteClientFidelite client = clientRepository.findByTelephone(telephone)
                .orElseThrow(() -> new EntityNotFoundException("Identifiant ou mot de passe incorrect.",
                        ErrorCodes.FIDELITE_NOT_FOUND));

        if (!client.isActif()) {
            throw new InvalidEntityException("Ce compte fidélité a été désactivé.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Compte inactif"));
        }

        if (!passwordEncoder.matches(dto.getMotDePasse(), client.getMotDePasse())) {
            throw new InvalidEntityException("Identifiant ou mot de passe incorrect.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Mot de passe invalide"));
        }

        int totalPoints = soldeRepository.totalPointsDuClient(client.getId());
        String jeton = jwtUtils.genererJetonPour(UserDetailsImpl.buildClient(client));

        return AuthClientResponseDto.builder()
                .jeton(jeton)
                .id(client.getId())
                .telephone(client.getTelephone())
                .nom(client.getNom())
                .prenom(client.getPrenom())
                .totalPoints(totalPoints)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProfilClientFideliteDto monProfil(Long idClient) {
        CompteClientFidelite client = clientRepository.findById(idClient)
                .orElseThrow(() -> new EntityNotFoundException("Compte client introuvable.", ErrorCodes.FIDELITE_NOT_FOUND));

        List<SoldePointsMagasin> soldes = soldeRepository.findAllByClientId(idClient);
        List<SoldePointsMagasinDto> soldesDto = soldes.stream()
                .map(s -> SoldePointsMagasinDto.builder()
                        .idEntreprise(s.getEntreprise().getId())
                        .nomMagasin(s.getEntreprise().getNom())
                        .ville(s.getEntreprise().getAdresse() != null ? s.getEntreprise().getAdresse().getVille() : null)
                        .logo(s.getEntreprise().getLogo())
                        .soldePoints(s.getSoldePoints())
                        .pointsCumulesTotal(s.getPointsCumulesTotal())
                        .montantParPoint(s.getEntreprise().getMontantParPoint())
                        .valeurPointFcfa(s.getEntreprise().getValeurPointFcfa())
                        .pointsMinimumBon(s.getEntreprise().getPointsMinimumBon())
                        .fideliteActive(s.getEntreprise().isFideliteActive())
                        .build())
                .toList();

        int totalPoints = soldes.stream().mapToInt(SoldePointsMagasin::getSoldePoints).sum();

        return ProfilClientFideliteDto.builder()
                .id(client.getId())
                .telephone(client.getTelephone())
                .nom(client.getNom())
                .prenom(client.getPrenom())
                .totalPoints(totalPoints)
                .soldesParMagasin(soldesDto)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MagasinPromotionDto> listerMagasinsEnPromotion(Long idClientOptionnel) {
        LocalDate aujourdhui = LocalDate.now();
        List<Entreprise> entreprises = entrepriseRepository.findAll();

        Map<Long, Integer> pointsParMagasin = (idClientOptionnel != null)
                ? soldeRepository.findAllByClientId(idClientOptionnel).stream()
                .collect(Collectors.toMap(s -> s.getEntreprise().getId(), SoldePointsMagasin::getSoldePoints))
                : Map.of();

        List<MagasinPromotionDto> resultats = new ArrayList<>();
        for (Entreprise ent : entreprises) {
            // Uniquement les magasins ayant un abonnement actif et le programme de fidelite actif
            if (ent.accesOuvert(aujourdhui) && ent.isFideliteActive()) {
                String adresseStr = ent.getAdresse() != null
                        ? String.join(" - ", List.of(
                                Optional.ofNullable(ent.getAdresse().getAdresse1()).orElse(""),
                                Optional.ofNullable(ent.getAdresse().getVille()).orElse("")
                        ).stream().filter(StringUtils::hasText).toList())
                        : null;

                resultats.add(MagasinPromotionDto.builder()
                        .id(ent.getId())
                        .nom(ent.getNom())
                        .description(ent.getDescription())
                        .ville(ent.getAdresse() != null ? ent.getAdresse().getVille() : null)
                        .adresse(adresseStr)
                        .telephone(ent.getTel())
                        .logo(ent.getLogo())
                        .montantParPoint(ent.getMontantParPoint())
                        .valeurPointFcfa(ent.getValeurPointFcfa())
                        .pointsMinimumBon(ent.getPointsMinimumBon())
                        .dureeValiditeBonJours(ent.getDureeValiditeBonJours())
                        .fideliteActive(ent.isFideliteActive())
                        .pointsClient(pointsParMagasin.getOrDefault(ent.getId(), 0))
                        .build());
            }
        }
        return resultats;
    }

    @Override
    @Transactional
    public ReclamationResultatDto reclamerTicket(Long idClient, String codeTicketSaisi) {
        CompteClientFidelite client = clientRepository.verrouiller(idClient)
                .orElseThrow(() -> new EntityNotFoundException("Compte client introuvable.", ErrorCodes.FIDELITE_NOT_FOUND));

        String code = CodeTicket.lire(codeTicketSaisi)
                .orElseThrow(() -> new InvalidEntityException("Format de code de ticket invalide. Douze caractères attendus.",
                        ErrorCodes.FIDELITE_NOT_VALID, List.of("Code invalide")));

        if (ticketReclameRepository.existsByCodeTicket(code)) {
            throw new InvalidEntityException("Ce ticket a déjà été enregistré et ses points ont déjà été réclamés.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Ticket déjà réclamé"));
        }

        Vente vente = venteRepository.findByCodeTicket(code)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun ticket ne porte ce code. S'il a été imprimé hors ligne, il apparaîtra dès que la caisse aura synchronisé.",
                        ErrorCodes.VENTE_NOT_FOUND));

        if (vente.isAnnulee()) {
            throw new InvalidEntityException("Cette vente a été annulée et ne donne droit à aucun point.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Vente annulée"));
        }

        Entreprise entreprise = entrepriseRepository.findById(vente.getIdEntreprise())
                .orElseThrow(() -> new EntityNotFoundException("Commerce émetteur introuvable.", ErrorCodes.ENTREPRISE_NOT_FOUND));

        if (!entreprise.accesOuvert(LocalDate.now())) {
            throw new InvalidEntityException("L'abonnement de ce commerce n'est plus actif.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Abonnement échu"));
        }

        if (!entreprise.isFideliteActive()) {
            throw new InvalidEntityException("Ce commerce ne participe pas au programme de fidélité.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Fidélité inactive"));
        }

        Facture facture = factureRepository.findByVenteId(vente.getId()).orElse(null);
        if (facture != null && facture.isAnnulee()) {
            throw new InvalidEntityException("La facture de cette vente a été annulée.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Facture annulée"));
        }

        // Sans facture, le total qui fait foi n'existe pas encore : ce n'est pas un ticket sans
        // valeur, c'est un ticket trop tot. Le client doit l'entendre, et pouvoir revenir.
        if (facture == null || facture.getTotalTtc() == null) {
            throw new InvalidEntityException(
                    "Ce ticket n'est pas encore facturé par le magasin. Réessayez un peu plus tard.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Ticket pas encore facturé"));
        }
        BigDecimal montantAchat = facture.getTotalTtc();

        int pointsGagnes = PointsFidelite.pour(montantAchat, entreprise.isFideliteActive(), entreprise.getMontantParPoint());
        if (pointsGagnes <= 0) {
            throw new InvalidEntityException(
                    "Le montant de ce ticket (" + montantAchat + " FCFA) ne donne droit à aucun point. Il faut au moins "
                            + entreprise.getMontantParPoint() + " FCFA pour gagner 1 point.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Points nuls"));
        }

        // 1. Enregistrer la reclamation
        TicketReclame reclamation = new TicketReclame();
        reclamation.setCodeTicket(code);
        reclamation.setClient(client);
        reclamation.setEntreprise(entreprise);
        reclamation.setVente(vente);
        reclamation.setPointsAttribues(pointsGagnes);
        reclamation.setMontantAchatTtc(montantAchat);
        reclamation.setDateReclamation(Instant.now());
        // Le verrou du compte ne protege que contre soi-meme : deux clients qui scannent le meme
        // ticket au meme instant passent tous deux le controle plus haut. La contrainte d'unicite
        // tranche, et le second recoit le meme refus que s'il etait arrive apres.
        try {
            ticketReclameRepository.saveAndFlush(reclamation);
        } catch (DataIntegrityViolationException collision) {
            throw new InvalidEntityException("Ce ticket a déjà été enregistré et ses points ont déjà été réclamés.",
                    ErrorCodes.FIDELITE_NOT_VALID, List.of("Ticket déjà réclamé"));
        }

        // 2. Mettre a jour le solde du client dans ce magasin
        SoldePointsMagasin solde = soldeRepository.findByClientIdAndEntrepriseId(client.getId(), entreprise.getId())
                .orElseGet(() -> {
                    SoldePointsMagasin nouveau = new SoldePointsMagasin();
                    nouveau.setClient(client);
                    nouveau.setEntreprise(entreprise);
                    nouveau.setSoldePoints(0);
                    nouveau.setPointsCumulesTotal(0);
                    return nouveau;
                });

        solde.setSoldePoints(solde.getSoldePoints() + pointsGagnes);
        solde.setPointsCumulesTotal(solde.getPointsCumulesTotal() + pointsGagnes);
        soldeRepository.save(solde);

        int totalPoints = soldeRepository.totalPointsDuClient(client.getId());

        return ReclamationResultatDto.builder()
                .codeTicket(code)
                .idEntreprise(entreprise.getId())
                .nomMagasin(entreprise.getNom())
                .montantAchatTtc(montantAchat)
                .pointsGagnes(pointsGagnes)
                .nouveauSoldeMagasin(solde.getSoldePoints())
                .nouveauTotalPoints(totalPoints)
                .dateAchat(vente.getDatevente())
                .dateReclamation(reclamation.getDateReclamation())
                .message("Bravo ! Vous avez gagné " + pointsGagnes + " point(s) chez " + entreprise.getNom())
                .build();
    }

    @Override
    @Transactional
    public BonDAchatDto convertirPointsEnBon(Long idClient, ConversionPointsDto dto) {
        if (dto == null || dto.getIdEntreprise() == null || dto.getPointsAConvertir() <= 0) {
            throw new InvalidEntityException("Informations de conversion invalides.",
                    ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Magasin et points positifs requis"));
        }

        CompteClientFidelite client = clientRepository.verrouiller(idClient)
                .orElseThrow(() -> new EntityNotFoundException("Compte client introuvable.", ErrorCodes.FIDELITE_NOT_FOUND));

        Entreprise entreprise = entrepriseRepository.findById(dto.getIdEntreprise())
                .orElseThrow(() -> new EntityNotFoundException("Magasin introuvable.", ErrorCodes.ENTREPRISE_NOT_FOUND));

        SoldePointsMagasin solde = soldeRepository.findByClientIdAndEntrepriseId(idClient, dto.getIdEntreprise())
                .orElseThrow(() -> new InvalidEntityException("Vous n'avez aucun point dans ce magasin.",
                        ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Solde inexistant")));

        if (solde.getSoldePoints() < dto.getPointsAConvertir()) {
            throw new InvalidEntityException(
                    "Solde insuffisant dans ce magasin. Vous avez " + solde.getSoldePoints() + " points.",
                    ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Points insuffisants"));
        }

        // La politique du magasin, telle qu'elle est le jour de l'echange.
        if (dto.getPointsAConvertir() < entreprise.getPointsMinimumBon()) {
            throw new InvalidEntityException(
                    "Il faut échanger au moins " + entreprise.getPointsMinimumBon() + " points chez "
                            + entreprise.getNom() + ".",
                    ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Sous le minimum du magasin"));
        }
        BigDecimal montantBon = PolitiqueFidelite.montantDuBon(dto.getPointsAConvertir(), entreprise.getValeurPointFcfa());
        if (montantBon.signum() <= 0) {
            throw new InvalidEntityException("Ces points ne valent pas encore un franc.",
                    ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Bon de montant nul"));
        }

        // Debiter les points du solde
        solde.setSoldePoints(solde.getSoldePoints() - dto.getPointsAConvertir());
        soldeRepository.save(solde);

        // Generer un code unique
        String codeBon;
        do {
            codeBon = CodeBonAchat.nouveau();
        } while (bonDAchatRepository.findByCodeBon(codeBon).isPresent());

        Instant emission = Instant.now();
        Instant expiration = emission.plus(entreprise.getDureeValiditeBonJours(), ChronoUnit.DAYS);

        BonDAchat bon = new BonDAchat();
        bon.setCodeBon(codeBon);
        bon.setClient(client);
        bon.setEntreprise(entreprise);
        bon.setPointsUtilises(dto.getPointsAConvertir());
        bon.setMontantFcfa(montantBon);
        bon.setStatut(StatutBonDAchat.ACTIF);
        bon.setDateEmission(emission);
        bon.setDateExpiration(expiration);

        BonDAchat enregistre = bonDAchatRepository.save(bon);

        return toBonDto(enregistre);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonDAchatDto> listerMesBons(Long idClient) {
        return bonDAchatRepository.findAllByClientIdOrderByDateEmissionDesc(idClient).stream()
                .map(this::toBonDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketHistoriqueDto> listerMesTickets(Long idClient) {
        return ticketReclameRepository.findAllByClientIdOrderByDateReclamationDesc(idClient).stream()
                .map(t -> TicketHistoriqueDto.builder()
                        .codeTicket(t.getCodeTicket())
                        .idEntreprise(t.getEntreprise().getId())
                        .nomMagasin(t.getEntreprise().getNom())
                        .montantAchatTtc(t.getMontantAchatTtc())
                        .pointsAttribues(t.getPointsAttribues())
                        .dateReclamation(t.getDateReclamation())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public BonDAchatDto verifierBon(String codeBonSaisi, Long idEntrepriseCaissier) {
        String code = CodeBonAchat.normaliser(codeBonSaisi)
                .orElseThrow(() -> new InvalidEntityException("Code de bon d'achat invalide.",
                        ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Format de code invalide")));

        BonDAchat bon = bonDAchatRepository.findByCodeBon(code)
                .orElseThrow(() -> new EntityNotFoundException("Aucun bon d'achat ne porte ce code : " + code,
                        ErrorCodes.BON_ACHAT_NOT_FOUND));

        if (idEntrepriseCaissier != null && !bon.getEntreprise().getId().equals(idEntrepriseCaissier)) {
            throw new InvalidEntityException("Ce bon d'achat est réservé au magasin : " + bon.getEntreprise().getNom(),
                    ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Magasin non correspondant"));
        }

        if (bon.getStatut() == StatutBonDAchat.ACTIF && bon.getDateExpiration().isBefore(Instant.now())) {
            bon.setStatut(StatutBonDAchat.EXPIRE);
            bonDAchatRepository.save(bon);
        }

        return toBonDto(bon);
    }

    @Override
    @Transactional
    public BonDAchatDto utiliserBon(String codeBonSaisi, Long idEntrepriseCaissier, Long idVenteOptionnel) {
        BonDAchatDto verif = verifierBon(codeBonSaisi, idEntrepriseCaissier);
        if (!verif.isUtilisable()) {
            throw new InvalidEntityException("Ce bon d'achat ne peut plus être utilisé (statut : " + verif.getStatut() + ").",
                    ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Bon non utilisable"));
        }

        // Relu sous verrou, et son statut avec : la verification ci-dessus a pu etre faite par deux
        // caisses a la fois, et seule la premiere a prendre le verrou doit le consommer.
        BonDAchat bon = bonDAchatRepository.verrouillerParCode(verif.getCodeBon())
                .orElseThrow(() -> new EntityNotFoundException("Bon introuvable", ErrorCodes.BON_ACHAT_NOT_FOUND));
        if (bon.getStatut() != StatutBonDAchat.ACTIF) {
            throw new InvalidEntityException("Ce bon d'achat ne peut plus être utilisé (statut : " + bon.getStatut() + ").",
                    ErrorCodes.BON_ACHAT_NOT_VALID, List.of("Bon non utilisable"));
        }

        bon.setStatut(StatutBonDAchat.UTILISE);
        bon.setDateUtilisation(Instant.now());
        if (idVenteOptionnel != null) {
            venteRepository.findById(idVenteOptionnel).ifPresent(bon::setVenteUtilisation);
        }

        BonDAchat enregistre = bonDAchatRepository.save(bon);
        return toBonDto(enregistre);
    }

    private BonDAchatDto toBonDto(BonDAchat bon) {
        boolean expire = bon.getDateExpiration().isBefore(Instant.now());
        StatutBonDAchat statut = bon.getStatut();
        if (statut == StatutBonDAchat.ACTIF && expire) {
            statut = StatutBonDAchat.EXPIRE;
        }

        boolean utilisable = statut == StatutBonDAchat.ACTIF && !expire;

        return BonDAchatDto.builder()
                .id(bon.getId())
                .codeBon(bon.getCodeBon())
                .idEntreprise(bon.getEntreprise().getId())
                .nomMagasin(bon.getEntreprise().getNom())
                .pointsUtilises(bon.getPointsUtilises())
                .montantFcfa(bon.getMontantFcfa())
                .statut(statut)
                .dateEmission(bon.getDateEmission())
                .dateExpiration(bon.getDateExpiration())
                .dateUtilisation(bon.getDateUtilisation())
                .utilisable(utilisable)
                .build();
    }
}
