package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Facture;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneFacture;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Reglement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneFactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ReglementRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.FactureService;
import com.jumpy.tech.gestionstock.gestiondestock.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FactureServiceImpl implements FactureService {

    /**
     * Deux decimales, arrondi commercial. Le cadrage est fixe ici, en un seul endroit : un
     * arrondi applique differemment a la ligne et au total fait apparaitre des ecarts d'un franc
     * que personne n'arrive ensuite a expliquer.
     */
    private static final int DECIMALES = 2;
    private static final RoundingMode ARRONDI = RoundingMode.HALF_UP;
    private static final BigDecimal CENT = new BigDecimal("100");

    private final FactureRepository factureRepository;
    private final LigneFactureRepository ligneFactureRepository;
    private final VenteRepository venteRepository;
    private final LigneVenteRepository ligneVenteRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final ReglementRepository reglementRepository;
    private final Cloisonnement cloisonnement;
    private final NotificationService notifications;

    public FactureServiceImpl(FactureRepository factureRepository,
                              LigneFactureRepository ligneFactureRepository,
                              VenteRepository venteRepository,
                              LigneVenteRepository ligneVenteRepository,
                              EntrepriseRepository entrepriseRepository,
                              ReglementRepository reglementRepository,
                              Cloisonnement cloisonnement,
                              NotificationService notifications) {
        this.notifications = notifications;
        this.entrepriseRepository = entrepriseRepository;
        this.reglementRepository = reglementRepository;
        this.cloisonnement = cloisonnement;
        this.factureRepository = factureRepository;
        this.ligneFactureRepository = ligneFactureRepository;
        this.venteRepository = venteRepository;
        this.ligneVenteRepository = ligneVenteRepository;
    }

    @Override
    @Transactional
    public FactureDto emettre(Long idVente) {
        Vente vente = vente(idVente);

        if (vente.isAnnulee()) {
            throw new InvalidEntityException("Une vente annulée ne se facture pas",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("On ne réclame pas le paiement d'une marchandise rendue"));
        }
        if (factureRepository.existsByVenteId(idVente)) {
            throw new InvalidEntityException("Cette vente est déjà facturée",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("La facture existante doit être annulée avant d'en émettre une autre"));
        }

        List<LigneVente> lignesVente = ligneVenteRepository.findAllByVenteId(idVente);
        if (lignesVente.isEmpty()) {
            throw new InvalidEntityException("Une vente sans ligne ne se facture pas",
                    ErrorCodes.VENTE_NOT_VALID);
        }

        // Le regime de TVA se lit une fois, sur l'entreprise de la vente, et vaut pour toute la
        // facture. Une entreprise non assujettie ne porte aucune TVA, quel que soit l'article.
        Entreprise entreprise = entrepriseDe(vente);
        boolean tvaApplicable = entreprise == null || entreprise.isAssujettieTva();

        Facture facture = new Facture();
        facture.setTvaApplicable(tvaApplicable);
        facture.setNumero(numeroSuivant());
        facture.setDateEmission(Instant.now());
        facture.setVente(vente);
        facture.setIdEntreprise(vente.getIdEntreprise());
        renseignerClient(facture, vente);
        facture.setTotalHt(BigDecimal.ZERO);
        facture.setTotalTva(BigDecimal.ZERO);
        facture.setTotalTtc(BigDecimal.ZERO);
        Facture enregistree = factureRepository.save(facture);

        List<LigneFacture> lignes = new ArrayList<>();
        BigDecimal totalHt = BigDecimal.ZERO;
        BigDecimal totalTva = BigDecimal.ZERO;

        for (LigneVente ligneVente : lignesVente) {
            LigneFacture ligne = figer(ligneVente, enregistree, entreprise, tvaApplicable);
            lignes.add(ligneFactureRepository.save(ligne));
            totalHt = totalHt.add(ligne.getMontantHt());
            totalTva = totalTva.add(ligne.getMontantTva());
        }

        // Les totaux somment les montants deja arrondis des lignes, et ne sont pas recalcules
        // depuis les quantites : c'est la seule facon que le total affiche corresponde a
        // l'addition des lignes que le client a sous les yeux.
        enregistree.setTotalHt(arrondi(totalHt));
        enregistree.setTotalTva(arrondi(totalTva));
        enregistree.setTotalTtc(arrondi(totalHt.add(totalTva)));

        log.info("Facture {} emise pour la vente {} : {} TTC",
                enregistree.getNumero(), idVente, enregistree.getTotalTtc());
        annoncerAuClient(enregistree, entreprise);
        return FactureDto.avecLignes(factureRepository.save(enregistree), lignes)
                .avecReglement(BigDecimal.ZERO);
    }

    /**
     * Met le courriel de la facture dans la file.
     *
     * Dans la transaction de l'emission : la facture et l'annonce de la facture tombent ensemble,
     * ou pas du tout. Ce n'est qu'une ligne a ecrire — la livraison, elle, viendra plus tard et
     * ailleurs, pour qu'un serveur SMTP tombe ne fasse jamais echouer une facturation.
     *
     * Un client sans adresse ne recoit rien, et ce n'est pas une anomalie : le ticket de caisse
     * reste le cas ordinaire.
     */
    private void annoncerAuClient(Facture facture, Entreprise entreprise) {
        Client client = facture.getClient();
        if (client == null || !StringUtils.hasText(client.getMail())) {
            return;
        }
        String maison = entreprise == null || entreprise.getNom() == null
                ? "Votre fournisseur" : entreprise.getNom();

        notifications.mettreEnFile(
                client.getMail(),
                "Votre facture " + facture.getNumero(),
                "Bonjour " + facture.getNomClient() + ",\n\n"
                        + "Votre facture " + facture.getNumero() + " s'élève à "
                        + facture.getTotalTtc() + " TTC.\n\n"
                        + "Cordialement,\n" + maison,
                facture.getIdEntreprise());
    }

    /**
     * Recopie la ligne de vente dans la facture.
     *
     * Le prix vient de la ligne de vente — c'est celui auquel on a vendu, et non le prix courant
     * de l'article, qui a pu changer depuis. Le taux de TVA, lui, n'existe que sur l'article :
     * faute de mieux, il est pris la et fige ici meme, pour qu'une revision du taux ne reecrive
     * pas les factures passees.
     */
    private LigneFacture figer(LigneVente ligneVente, Facture facture, Entreprise entreprise,
                               boolean tvaApplicable) {
        Article article = ligneVente.getArticles();

        BigDecimal quantite = ligneVente.getQuantite() == null ? BigDecimal.ZERO : ligneVente.getQuantite();
        BigDecimal prixUnitaire = prixUnitaire(ligneVente, article);
        BigDecimal tauxTva = tauxTva(article, entreprise, tvaApplicable);

        BigDecimal montantHt = arrondi(quantite.multiply(prixUnitaire));
        BigDecimal montantTva = arrondi(montantHt.multiply(tauxTva).divide(CENT, DECIMALES + 2, ARRONDI));

        LigneFacture ligne = new LigneFacture();
        ligne.setFacture(facture);
        ligne.setCodeArticle(article == null ? null : article.getCodeArticle());
        ligne.setDesignation(article == null ? null : article.getDesignation());
        ligne.setQuantite(quantite);
        ligne.setPrixUnitaireHt(prixUnitaire);
        ligne.setTauxTva(tauxTva);
        ligne.setMontantHt(montantHt);
        ligne.setMontantTva(montantTva);
        ligne.setMontantTtc(arrondi(montantHt.add(montantTva)));
        return ligne;
    }

    /**
     * Le taux applique a une ligne.
     *
     * L'entreprise decide d'abord : non assujettie, rien n'est facture en TVA, et un article qui
     * porterait un taux ne peut pas le lui imposer. Assujettie, le taux de l'article l'emporte
     * s'il en fixe un — c'est ainsi qu'un produit exonere ou a taux reduit reste une exception
     * portee par l'article — et a defaut on prend celui de l'entreprise.
     *
     * Sans entreprise rattachee a la vente, on retombe sur le seul taux connu, celui de
     * l'article : le rattachement multi-entreprise n'est pas encore effectif, et une facture ne
     * doit pas perdre sa TVA a cause de cela.
     */
    private BigDecimal tauxTva(Article article, Entreprise entreprise, boolean tvaApplicable) {
        if (!tvaApplicable) {
            return BigDecimal.ZERO;
        }
        if (article != null && article.getTauxTva() != null) {
            return article.getTauxTva();
        }
        if (entreprise != null && entreprise.getTauxTva() != null) {
            return entreprise.getTauxTva();
        }
        return BigDecimal.ZERO;
    }

    /**
     * L'entreprise de la vente, quand elle en designe une.
     *
     * `idEntreprise` est porte par la vente mais reste souvent vide : le cloisonnement par
     * entreprise n'a jamais ete rendu effectif dans cette application. Tant qu'il ne l'est pas,
     * une vente sans entreprise facture comme avant.
     */
    private Entreprise entrepriseDe(Vente vente) {
        if (vente.getIdEntreprise() == null) {
            return null;
        }
        return entrepriseRepository.findById(vente.getIdEntreprise()).orElse(null);
    }

    /**
     * Le prix de la ligne de vente fait foi. Il peut manquer — rien ne l'imposait jusqu'ici — et
     * le prix courant de l'article sert alors de repli, faute de meilleure source.
     */
    private BigDecimal prixUnitaire(LigneVente ligneVente, Article article) {
        if (ligneVente.getPrixUnitaire() != null) {
            return ligneVente.getPrixUnitaire();
        }
        if (article != null && article.getPrixUnitaire() != null) {
            return article.getPrixUnitaire();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Le client de la vente, quand il est connu.
     *
     * Il se lit directement sur la vente, qu'elle vienne du comptoir ou d'une commande : servir
     * une commande y recopie son client, de sorte qu'il n'y a ici qu'un seul chemin a suivre. Une
     * vente anonyme donne une facture anonyme — c'est le ticket de caisse, pas une anomalie.
     *
     * Le nom est recopie a cote de l'identifiant : un client renomme ou supprime ne doit pas
     * changer une facture deja remise.
     */
    private void renseignerClient(Facture facture, Vente vente) {
        Client client = vente.getClient();
        if (client == null) {
            return;
        }
        facture.setClient(client);
        facture.setNomClient(nomComplet(client));
    }

    private String nomComplet(Client client) {
        String noms = client.getNom() == null ? "" : client.getNom().trim();
        String prenoms = client.getPrenoms() == null ? "" : client.getPrenoms().trim();
        return (noms + " " + prenoms).trim();
    }

    /** Format `FA-2026-000012` : l'annee se lit sans ouvrir la facture, le rang ne se rejoue pas. */
    private String numeroSuivant() {
        long rang = factureRepository.prochainNumero();
        int annee = Instant.now().atZone(ZoneId.systemDefault()).getYear();
        return String.format("FA-%d-%06d", annee, rang);
    }

    private BigDecimal arrondi(BigDecimal montant) {
        return montant.setScale(DECIMALES, ARRONDI);
    }

    @Override
    public FactureDto findById(Long id) {
        Facture facture = facture(id);
        return FactureDto.avecLignes(facture, ligneFactureRepository.findAllByFactureId(id))
                .avecReglement(reglementRepository.totalReglePour(id));
    }

    @Override
    public FactureDto findByNumero(String numero) {
        if (!StringUtils.hasLength(numero)) {
            throw new InvalidEntityException("Aucune facture ne peut être cherchée sans numéro",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        Facture facture = (cloisonnement.filtre()
                ? factureRepository.findByNumeroAndIdEntreprise(numero, cloisonnement.entrepriseCourante())
                : factureRepository.findByNumero(numero))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune facture portant le numéro " + numero + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
        return FactureDto.avecLignes(facture, ligneFactureRepository.findAllByFactureId(facture.getId()))
                .avecReglement(reglementRepository.totalReglePour(facture.getId()));
    }

    @Override
    public FactureDto findByVente(Long idVente) {
        // La vente est verifiee d'abord : demander la facture d'une vente qui n'est pas la sienne
        // ne doit pas en reveler l'existence.
        vente(idVente);
        Facture facture = factureRepository.findByVenteId(idVente)
                .orElseThrow(() -> new EntityNotFoundException(
                        "La vente " + idVente + " n'a pas été facturée",
                        ErrorCodes.VENTE_NOT_FOUND));
        return FactureDto.avecLignes(facture, ligneFactureRepository.findAllByFactureId(facture.getId()))
                .avecReglement(reglementRepository.totalReglePour(facture.getId()));
    }

    @Override
    public Page<FactureDto> findAll(Pageable pageable) {
        return rechercher(null, null, pageable);
    }

    /**
     * Les factures, filtrables par numero, par client et par ce qu'il reste a encaisser.
     *
     * « Qui me doit de l'argent » est la question du comptable, et la liste paginee n'y repondait
     * pas : il fallait feuilleter toutes les factures en lisant les statuts un par un.
     */
    @Override
    public Page<FactureDto> rechercher(String q, String statut, Pageable pageable) {
        // Sans les lignes : une liste de factures affiche des totaux, pas le detail de chacune.
        Page<Facture> page = factureRepository.rechercher(
                cloisonnement.filtre(),
                cloisonnement.filtre() ? cloisonnement.entrepriseCourante() : null,
                RechercheUtils.normaliser(q),
                RechercheUtils.normaliser(statut),
                pageable);

        // Les montants regles de toute la page en une requete : les demander facture par facture
        // ferait une requete par ligne affichee.
        Map<Long, BigDecimal> regles = totauxRegles(page.getContent());
        return page.map(facture -> FactureDto.fromEntity(facture)
                .avecReglement(regles.getOrDefault(facture.getId(), BigDecimal.ZERO)));
    }

    private Map<Long, BigDecimal> totauxRegles(List<Facture> factures) {
        if (factures.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = factures.stream().map(Facture::getId).collect(Collectors.toList());
        return reglementRepository.totauxRegles(ids).stream()
                .collect(Collectors.toMap(ligne -> (Long) ligne[0], ligne -> (BigDecimal) ligne[1]));
    }

    @Override
    @Transactional
    public FactureDto annuler(Long id) {
        Facture facture = facture(id);
        if (facture.isAnnulee()) {
            throw new InvalidEntityException("Cette facture est déjà annulée",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        // Annuler une facture deja encaissee laisserait de l'argent recu sans rien en face. Les
        // reglements se reprennent d'abord — c'est le moment ou l'on decide de rembourser.
        if (reglementRepository.existsByFactureId(id)) {
            throw new InvalidEntityException(
                    "Cette facture porte des règlements : reprenez-les avant de l'annuler",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("Encaissé sur " + facture.getNumero() + " : "
                            + reglementRepository.totalReglePour(id)));
        }
        facture.setAnnulee(true);
        return FactureDto.avecLignes(factureRepository.save(facture),
                ligneFactureRepository.findAllByFactureId(id))
                .avecReglement(BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public ReglementDto regler(Long idFacture, ReglementDto demande) {
        Facture facture = facture(idFacture);

        if (facture.isAnnulee()) {
            throw new InvalidEntityException("Une facture annulée ne se règle pas",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("La facture " + facture.getNumero() + " a été annulée"));
        }
        if (demande == null || demande.getMontant() == null || demande.getMontant().signum() <= 0) {
            throw new InvalidEntityException("Le montant d'un règlement doit être strictement positif",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        if (demande.getMode() == null) {
            throw new InvalidEntityException("Un règlement dit par quel moyen il a été reçu",
                    ErrorCodes.VENTE_NOT_VALID);
        }

        BigDecimal dejaRegle = reglementRepository.totalReglePour(idFacture);
        BigDecimal reste = facture.getTotalTtc().subtract(dejaRegle);
        if (reste.signum() <= 0) {
            throw new InvalidEntityException("Cette facture est déjà réglée",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        // Un trop-percu est une erreur de saisie, pas une situation a enregistrer : le refuser
        // evite d'avoir a inventer plus tard une notion de rendu de monnaie.
        if (demande.getMontant().compareTo(reste) > 0) {
            throw new InvalidEntityException(
                    "Le règlement dépasse le reste à payer : " + reste + " attendus, "
                            + demande.getMontant() + " présentés",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("Reste à payer sur " + facture.getNumero() + " : " + reste));
        }

        Reglement reglement = new Reglement();
        reglement.setFacture(facture);
        reglement.setMontant(demande.getMontant());
        reglement.setMode(demande.getMode());
        reglement.setReference(demande.getReference());
        // La date est celle de l'encaissement, pas celle que l'appelant declare : antidater un
        // reglement deplacerait une recette d'un exercice a l'autre.
        reglement.setDateReglement(Instant.now());
        reglement.setIdEntreprise(facture.getIdEntreprise());

        log.info("Reglement de {} sur la facture {} ({})",
                demande.getMontant(), facture.getNumero(), demande.getMode());
        return ReglementDto.fromEntity(reglementRepository.save(reglement));
    }

    @Override
    public List<ReglementDto> reglements(Long idFacture) {
        facture(idFacture);
        return reglementRepository.findAllByFactureIdOrderByDateReglementAsc(idFacture).stream()
                .map(ReglementDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void supprimerReglement(Long idFacture, Long idReglement) {
        facture(idFacture);
        Reglement reglement = reglementRepository.findById(idReglement)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun règlement avec l'identifiant " + idReglement + " n'a été trouvé",
                        ErrorCodes.VENTE_NOT_FOUND));
        // Sans ce controle, connaitre un identifiant de reglement suffirait a effacer une recette
        // portee par la facture d'un autre.
        if (reglement.getFacture() == null || !idFacture.equals(reglement.getFacture().getId())) {
            throw new InvalidEntityException(
                    "Le règlement " + idReglement + " ne porte pas sur la facture " + idFacture,
                    ErrorCodes.VENTE_NOT_VALID);
        }
        reglementRepository.delete(reglement);
    }

    private Facture facture(Long id) {
        if (id == null) {
            throw new InvalidEntityException("Aucune facture ne peut être cherchée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        Facture facture = factureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune facture avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
        cloisonnement.verifierAcces(facture.getIdEntreprise(), "facture", id);
        return facture;
    }

    private Vente vente(Long id) {
        if (id == null) {
            throw new InvalidEntityException("Aucune vente ne peut être facturée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        Vente vente = venteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
        cloisonnement.verifierAcces(vente.getIdEntreprise(), "vente", id);
        return vente;
    }
}
