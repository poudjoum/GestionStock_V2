package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.conditionnement.Conditionnements;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import com.jumpy.tech.gestionstock.gestiondestock.entities.PromotionArticle;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.PrixDuJour;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeClient;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeClient;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.fidelite.CodeTicket;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CommandeClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.FactureService;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import com.jumpy.tech.gestionstock.gestiondestock.service.VenteService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.VenteValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VenteServiceImpl implements VenteService {

    /**
     * De combien l'horloge d'un poste de vente peut avancer sans qu'on refuse ses ventes.
     *
     * Celle d'un telephone derive, et refuser une vente pour deux minutes d'avance rendrait la
     * synchronisation capricieuse. Au-dela, la date est fausse, et la retenir fausserait la
     * caisse d'une journee entiere.
     */
    private static final Duration TOLERANCE_HORLOGE = Duration.ofMinutes(5);

    private final VenteRepository venteRepository;
    private final ArticleRepository articleRepository;
    private final LigneVenteRepository ligneVenteRepository;
    private final FactureRepository factureRepository;
    private final CommandeClientRepository commandeClientRepository;
    private final LigneCmndeClientRepository ligneCmndeClientRepository;
    private final ClientRepository clientRepository;
    private final Cloisonnement cloisonnement;
    private final MvtStkService mvtStkService;
    private final FactureService factureService;
    private final PrixDuJour prixDuJour;
    private final Conditionnements conditionnements;
    private final SiteCourant siteCourant;

    public VenteServiceImpl(VenteRepository venteRepository, ArticleRepository articleRepository,
                            LigneVenteRepository ligneVenteRepository,
                            FactureRepository factureRepository,
                            CommandeClientRepository commandeClientRepository,
                            LigneCmndeClientRepository ligneCmndeClientRepository,
                            ClientRepository clientRepository,
                            Cloisonnement cloisonnement,
                            MvtStkService mvtStkService,
                            FactureService factureService,
                            PrixDuJour prixDuJour,
                            Conditionnements conditionnements,
                            SiteCourant siteCourant) {
        this.siteCourant = siteCourant;
        this.conditionnements = conditionnements;
        this.factureService = factureService;
        this.prixDuJour = prixDuJour;
        this.clientRepository = clientRepository;
        this.cloisonnement = cloisonnement;
        this.venteRepository = venteRepository;
        this.articleRepository = articleRepository;
        this.ligneVenteRepository = ligneVenteRepository;
        this.factureRepository = factureRepository;
        this.commandeClientRepository = commandeClientRepository;
        this.ligneCmndeClientRepository = ligneCmndeClientRepository;
        this.mvtStkService = mvtStkService;
    }

    /**
     * Enregistre la vente, ses lignes, et la sortie de stock de chaque ligne.
     *
     * Trois choses manquaient. Les lignes n'etaient jamais ecrites — `ligneVenteRepository` etait
     * injecte sans etre appele — donc une vente se resumait a un en-tete sans contenu. Aucun
     * mouvement de stock n'etait enregistre, donc vendre ne retirait rien du magasin. Et les deux
     * ecritures n'avaient pas de transaction commune.
     *
     * Le tout est maintenant dans une seule transaction : si une ligne manque de stock, la vente
     * entiere est annulee. Vendre la moitie d'un panier sans le dire serait pire que refuser.
     */
    @Override
    @Transactional
    public VenteDto save(VenteDto dto) {
        return enregistrer(dto, null);
    }

    /**
     * Une vente qui a deja eu lieu, sur un poste sans reseau.
     *
     * La reference et la date sont exigees ici, la ou elles sont facultatives en vente directe :
     * sans reference, un envoi rejoue ferait une seconde vente ; sans date, la vente se rangerait
     * dans la caisse du moment ou elle arrive, et non de celui ou elle a eu lieu.
     */
    @Override
    @Transactional
    public VenteDto synchroniser(VenteDto dto) {
        if (dto == null || !StringUtils.hasText(dto.getReferenceClient())) {
            throw new InvalidEntityException(
                    "Une vente synchronisée porte la référence tirée par le poste de vente",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("Sans elle, un envoi rejoué enregistrerait une seconde vente"));
        }
        if (dto.getDatevente() == null) {
            throw new InvalidEntityException(
                    "Une vente synchronisée porte la date à laquelle elle a eu lieu",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("Sans elle, la vente pèserait sur la caisse du jour de l'envoi"));
        }
        VenteDto vente = enregistrer(dto, dateDeVenteValide(dto.getDatevente()));

        // L'encaissement du comptoir, dans la meme transaction : la vente, sa facture et son
        // reglement arrivent ensemble ou pas du tout. Une vente enregistree sans son encaissement
        // laisserait l'argent du tiroir sans trace, et le poste ne saurait pas quoi renvoyer.
        //
        // La date est celle de la vente enregistree, et non celle de cet envoi : sur un envoi
        // rejoue, c'est la meme, et c'est elle qui range l'argent dans la bonne caisse.
        //
        // Une vente annulee depuis ne se facture pas ; l'envoi reussit quand meme, sans quoi il
        // resterait en file sur le poste pour toujours.
        if (dto.getEncaissement() != null && !vente.isAnnulee()) {
            factureService.encaisserVenteSynchronisee(
                    vente.getId(), dto.getEncaissement(), vente.getDatevente());
        }
        return vente;
    }

    /**
     * Une vente ne se date pas dans l'avenir.
     *
     * Quelques minutes de tolerance : l'horloge d'un telephone derive, et refuser une vente pour
     * deux minutes d'avance rendrait la synchronisation capricieuse. Au-dela, la date est fausse
     * et la retenir fausserait durablement la caisse.
     */
    private Instant dateDeVenteValide(Instant datevente) {
        Instant limite = Instant.now().plus(TOLERANCE_HORLOGE);
        if (datevente.isAfter(limite)) {
            throw new InvalidEntityException(
                    "Une vente ne peut pas être datée dans le futur : " + datevente,
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("L'horloge du poste de vente est probablement à régler"));
        }
        return datevente;
    }

    /**
     * Le chemin commun aux deux facons d'enregistrer une vente.
     *
     * `quand` est nul pour une vente directe — elle a lieu maintenant — et porte la date reelle
     * pour une vente synchronisee. C'est ce seul parametre qui decide de tout le reste : la date
     * des mouvements, et si le stock s'oppose ou non a la sortie.
     */
    private VenteDto enregistrer(VenteDto dto, Instant quand) {
        List<String> errors = VenteValidator.validate(dto);
        if (!errors.isEmpty()) {
            log.error("Vente not Valid {}", dto);
            // Le code rendu etait VENTE_NOT_FOUND pour une vente invalide.
            throw new InvalidEntityException("La vente n'est pas valide", ErrorCodes.VENTE_NOT_VALID, errors);
        }

        // Rejouer un envoi rend la vente deja enregistree. C'est ce qui permet a un poste qui a
        // perdu le reseau de reessayer sans risquer une seconde sortie de stock.
        VenteDto dejaEnregistree = venteDejaRecue(dto.getReferenceClient());
        if (dejaEnregistree != null) {
            return dejaEnregistree;
        }

        List<LigneVenteDto> lignes = dto.getLigneVente() == null ? List.of() : dto.getLigneVente();
        if (lignes.isEmpty()) {
            throw new InvalidEntityException("Une vente sans ligne ne vend rien",
                    ErrorCodes.VENTE_NOT_VALID, List.of("Aucune ligne de vente fournie"));
        }

        List<String> articleErrors = new ArrayList<>();
        Map<Long, Article> articlesCharges = new HashMap<>();
        for (LigneVenteDto ligne : lignes) {
            if (ligne.getArticle() == null || ligne.getArticle().getId() == null) {
                articleErrors.add("Impossible d'enregistrer une vente sans article");
                continue;
            }
            Optional<Article> article = articleRepository.findById(ligne.getArticle().getId());
            if (article.isEmpty()) {
                articleErrors.add("L'article avec l'identifiant " + ligne.getArticle().getId()
                        + " n'existe pas");
            } else {
                articlesCharges.put(article.get().getId(), article.get());
            }
        }
        if (!articleErrors.isEmpty()) {
            log.error("One or more article were not in Database, {}", articleErrors);
            // Les erreurs remontees etaient `errors`, la liste de validation, toujours vide a ce
            // stade : le client recevait un 400 sans savoir quel article posait probleme.
            throw new InvalidEntityException(
                    "Un ou plusieurs articles de la vente n'existent pas",
                    ErrorCodes.VENTE_NOT_VALID, articleErrors);
        }

        Vente aEnregistrer = VenteDto.toEntity(dto);
        // L'entreprise vient du compte connecte et ecrase ce que dirait la requete : une vente ne
        // s'enregistre pas chez le voisin.
        if (cloisonnement.filtre()) {
            aEnregistrer.setIdEntreprise(cloisonnement.entrepriseCourante());
        }
        // Une vente directe a lieu maintenant ; une vente synchronisee porte la date qu'elle
        // avait sur le poste, et la date envoyee ne fait donc foi que dans ce second cas.
        aEnregistrer.setDatevente(quand == null ? Instant.now() : quand);
        Site magasin = magasinDeLaVente(dto, quand);
        aEnregistrer.setSite(magasin);
        aEnregistrer.setSiteExpedition(magasin);
        aEnregistrer.setCodeTicket(codeDeTicket(dto.getCodeTicket()));
        // Le client est facultatif : la vente de comptoir anonyme reste le cas ordinaire.
        if (dto.getClient() != null && dto.getClient().getId() != null) {
            aEnregistrer.setClient(client(dto.getClient().getId()));
        }

        Vente savedVente;
        try {
            savedVente = venteRepository.saveAndFlush(aEnregistrer);
        } catch (DataIntegrityViolationException collision) {
            // Deux envois de la meme vente partis en meme temps : le premier a gagne l'index
            // unique. Le second retrouve son travail deja fait, ce qui est exactement ce qu'il
            // demandait. Sans ce rattrapage, un simple double appui ferait un 500.
            VenteDto gagnante = venteDejaRecue(dto.getReferenceClient());
            if (gagnante != null) {
                log.info("Vente {} deja enregistree par un envoi concurrent", dto.getReferenceClient());
                return gagnante;
            }
            throw collision;
        }

        Map<Long, PromotionArticle> promotions = prixDuJour.promotions(savedVente.getIdEntreprise(),
                savedVente.getDatevente());
        List<String> avertissements = new ArrayList<>();
        for (LigneVenteDto ligneDto : lignes) {
            LigneVente ligne = LigneVenteDto.toEntity(ligneDto);
            Article article = articlesCharges.get(ligneDto.getArticle().getId());
            // Au comptoir, le carton doit etre en vente ; une vente faite hors ligne a deja eu lieu,
            // et le carton qu'on a retire depuis n'en a pas moins ete vendu.
            Conditionnement conditionnement = quand == null
                    ? conditionnements.pourLigne(article, ligneDto.getConditionnement(), Conditionnements.Usage.VENTE)
                    : conditionnements.pourLigneConstatee(article, ligneDto.getConditionnement());
            if (quand == null) {
                Conditionnements.verifierFraction(article, conditionnement, ligne.getQuantite(),
                        ErrorCodes.LIGNE_VENTE_NOT_VALID);
            }
            ligne.setConditionnement(conditionnement);
            ligne.setContenance(Conditionnements.contenance(conditionnement));
            BigDecimal prixCatalogue = Conditionnements.prixCatalogue(article, conditionnement);
            PromotionArticle promotion = promotions.get(article.getId());
            if (quand == null) {
                // Au comptoir, maintenant : le prix de la promotion, meme si la caisse l'ignorait.
                ligne.setPrixUnitaire(PrixDuJour.pourLigne(ligne.getPrixUnitaire(), prixCatalogue, promotion,
                        conditionnement != null));
            } else if (ligne.getPrixUnitaire() == null) {
                // Une vente faite hors ligne garde le prix que le client a paye et que son ticket
                // imprime. Le serveur ne le complete que s'il manque.
                ligne.setPrixUnitaire(PrixDuJour.pourLigne(null, prixCatalogue, promotion, conditionnement != null));
            }
            // L'article charge, et non celui que construit le DTO, qui ne porte que son
            // identifiant. Quand la facture est emise dans la meme transaction — une vente
            // synchronisee avec son encaissement —, elle lit la ligne telle qu'elle est en memoire :
            // avec l'article du DTO, elle figeait une designation et un code vides, et perdait le
            // taux de TVA propre a l'article.
            ligne.setArticles(article);
            ligne.setVente(savedVente);
            ligneVenteRepository.save(ligne);
            avertissements.addAll(sortirDuStock(ligne, ligneDto.getIdLot(), savedVente, quand));
        }

        VenteDto resultat = VenteDto.fromEntity(savedVente);
        resultat.setAvertissements(avertissements);
        return resultat;
    }

    /**
     * Le magasin ou la vente a lieu.
     *
     * Au comptoir, le site actif — et il doit vendre : un entrepot livre, il n'encaisse pas.
     * Hors ligne, celui ou se trouvait le poste, qu'il envoie avec la vente : le caissier a pu
     * changer de site depuis, la vente n'en a pas moins eu lieu la ou elle a eu lieu. Elle n'est
     * alors pas refusee pour un entrepot : elle est faite, la refuser la laisserait en file pour
     * toujours.
     */
    private Site magasinDeLaVente(VenteDto dto, Instant quand) {
        Site site = quand != null && dto.getIdSite() != null
                ? siteCourant.accessible(dto.getIdSite())
                : siteCourant.site();
        if (site != null && quand == null && !site.vend()) {
            throw new InvalidEntityException(
                    "« " + site.getNom() + " » est un entrepôt : il ne vend pas au comptoir",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("Choisissez un magasin, ou faites une commande client livrée depuis l'entrepôt"));
        }
        return site;
    }

    /**
     * Le code de ticket de la vente : celui du poste de vente, ou un neuf.
     *
     * Celui du poste est garde tel quel des qu'il a la bonne forme — il est deja imprime sur le
     * papier du client, hors ligne peut-etre. Mal forme, il est refuse plutot que remplace : le
     * ticket imprime porterait un code que la base ne connaitrait pas.
     */
    private static String codeDeTicket(String propose) {
        if (!StringUtils.hasText(propose)) {
            return CodeTicket.nouveau();
        }
        return CodeTicket.lire(propose).orElseThrow(() -> new InvalidEntityException(
                "Le code de ticket n'a pas la forme attendue : " + propose,
                ErrorCodes.VENTE_NOT_VALID,
                List.of("Douze caractères parmi " + CodeTicket.ALPHABET)));
    }

    /**
     * La vente deja enregistree sous cette reference, ou `null`.
     *
     * Une reference trouvee chez une autre entreprise ne rend rien : elle est traitee comme
     * inconnue, et l'insertion echouera sur l'index unique. Rendre la vente du voisin parce que
     * l'on a devine sa reference serait une fuite.
     */
    private VenteDto venteDejaRecue(String referenceClient) {
        if (!StringUtils.hasText(referenceClient)) {
            return null;
        }
        return venteRepository.findVenteByReferenceClient(referenceClient)
                .filter(vente -> !cloisonnement.filtre()
                        || java.util.Objects.equals(vente.getIdEntreprise(),
                                cloisonnement.entrepriseCourante()))
                .map(VenteDto::fromEntity)
                .orElse(null);
    }

    /**
     * La vente est le moment ou la marchandise quitte le magasin ; c'est donc ici, et pas a la
     * commande client, que le stock diminue. Une commande n'est qu'un engagement : tant qu'elle
     * n'est pas servie, rien n'est sorti des rayons.
     *
     * Rend les avertissements de la sortie : un lot DLUO depasse est parti, le caissier le dit.
     */
    private List<String> sortirDuStock(LigneVente ligne, Long idLot, Vente vente, Instant quand) {
        MvtStkDto mouvement = MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ligne.getArticles().getId()).build())
                .quantite(enStock(ligne))
                .motif(MotifMvtStk.VENTE)
                .idEntreprise(vente.getIdEntreprise())
                .idSite(idSite(vente.getSiteExpedition()))
                // Le lot scanne sort, et non le premier perime.
                .idLot(idLot)
                .idVente(vente.getId())
                .build();
        MvtStkDto fait;
        if (quand == null) {
            fait = mvtStkService.sortieStock(mouvement);
        } else {
            // La marchandise est deja partie : le mouvement porte la date de la vente, et le
            // stock ne s'y oppose pas. S'il passe sous zero, c'est le signal qu'un inventaire est
            // a faire — pas une raison d'effacer une vente qui a eu lieu.
            fait = mvtStkService.sortieConstatee(mouvement, quand);
        }
        return fait == null || fait.getAvertissements() == null ? List.of() : fait.getAvertissements();
    }

    @Override
    public List<LigneVenteDto> lignes(Long idVente) {
        vente(idVente);
        return ligneVenteRepository.findAllByVenteId(idVente).stream()
                .map(LigneVenteDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Ajoute un article a une vente en cours. C'est le geste du comptoir : la vente se construit
     * article par article, sans qu'il faille connaitre tout le panier d'avance.
     *
     * La sortie de stock est immediate, comme a l'enregistrement de la vente, et porte le meme
     * motif : ce n'est pas une correction, c'est de la marchandise vendue.
     */
    @Override
    @Transactional
    public LigneVenteDto ajouterLigne(Long idVente, LigneVenteDto ligne) {
        Vente vente = venteModifiable(idVente);
        if (ligne == null || ligne.getArticle() == null || ligne.getArticle().getId() == null) {
            throw new InvalidEntityException("Une ligne de vente désigne un article",
                    ErrorCodes.LIGNE_VENTE_NOT_VALID);
        }
        Article article = articleRepository.findById(ligne.getArticle().getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + ligne.getArticle().getId() + " n'a été trouvé",
                        ErrorCodes.ARTICLE_NOT_FOUND));
        BigDecimal quantite = quantiteValide(ligne.getQuantite());
        Conditionnement conditionnement = conditionnements.pourLigne(article, ligne.getConditionnement(),
                Conditionnements.Usage.VENTE);
        Conditionnements.verifierFraction(article, conditionnement, quantite, ErrorCodes.LIGNE_VENTE_NOT_VALID);
        BigDecimal contenance = Conditionnements.contenance(conditionnement);

        // Le stock est debite avant l'ecriture de la ligne : s'il manque, la transaction echoue et
        // la vente reste telle qu'elle etait.
        mvtStkService.sortieStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(article.getId()).build())
                .quantite(Conditionnements.enUnitesDeBase(quantite, contenance))
                .motif(MotifMvtStk.VENTE)
                .idSite(idSite(vente.getSiteExpedition()))
                .idLot(ligne.getIdLot())
                .idVente(vente.getId())
                .build());

        LigneVente nouvelle = new LigneVente();
        nouvelle.setVente(vente);
        nouvelle.setArticles(article);
        nouvelle.setQuantite(quantite);
        nouvelle.setConditionnement(conditionnement);
        nouvelle.setContenance(contenance);
        nouvelle.setPrixUnitaire(PrixDuJour.pourLigne(ligne.getPrixUnitaire(),
                Conditionnements.prixCatalogue(article, conditionnement),
                prixDuJour.promotions(vente.getIdEntreprise(), null).get(article.getId()),
                conditionnement != null));
        nouvelle.setIdEntreprise(vente.getIdEntreprise());

        return LigneVenteDto.fromEntity(ligneVenteRepository.save(nouvelle));
    }

    /**
     * Attribue ou change le client d'une vente.
     *
     * Le caissier ne sait pas toujours d'avance a qui il vend : le client se presente, ou se fait
     * connaitre au moment de payer. Tant que la vente n'est ni annulee ni facturee, elle peut
     * donc recevoir son nom.
     */
    @Override
    @Transactional
    public VenteDto attribuerClient(Long idVente, Long idClient) {
        Vente vente = venteModifiable(idVente);
        vente.setClient(client(idClient));
        return VenteDto.fromEntity(venteRepository.save(vente));
    }

    private Client client(Long idClient) {
        if (idClient == null) {
            throw new InvalidEntityException("Aucun client ne peut être désigné sans identifiant",
                    ErrorCodes.CLIENT_NOT_VALID);
        }
        return clientRepository.findById(idClient)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun client avec l'identifiant " + idClient + " n'a été trouvé",
                        ErrorCodes.CLIENT_NOT_FOUND));
    }

    /**
     * Cree la vente qui sert une commande client.
     *
     * La commande doit etre VALIDEE : servir une commande encore en preparation reviendrait a
     * sortir une marchandise que personne n'a confirmee. La vente reprend les lignes telles
     * qu'elles sont au moment ou on sert, et la commande passe LIVREE — ce qui la fige a son tour.
     */
    @Override
    @Transactional
    public VenteDto servirCommandeClient(Long idCommandeClient) {
        return servirCommandeClient(idCommandeClient, null);
    }

    @Override
    @Transactional
    public VenteDto servirCommandeClient(Long idCommandeClient, List<LigneReceptionDto> partiel) {
        CommandeClient commande = commandeClientRepository.findById(idCommandeClient)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune commande client avec l'identifiant " + idCommandeClient + " n'a été trouvée",
                        ErrorCodes.COMMANDE_CLIENT_NOT_FOUND));

        // Une commande deja servie en partie se sert encore : c'est tout l'interet du service
        // partiel, le reliquat part quand la marchandise arrive.
        if (commande.getEtat() != EtatCommande.VALIDEE
                && commande.getEtat() != EtatCommande.PARTIELLEMENT_LIVREE) {
            throw new InvalidEntityException(
                    "Seule une commande VALIDEE se sert, celle-ci est " + commande.getEtat(),
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
                    List.of("Validez la commande avant de la servir"));
        }

        List<LigneCmndeClient> lignesCommande = ligneCmndeClientRepository.findAllByCommandeClientId(idCommandeClient);
        if (lignesCommande.isEmpty()) {
            throw new InvalidEntityException("Une commande sans ligne ne se sert pas",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID);
        }

        // Sans precision, on sert tout ce qui reste du : c'est le geste ordinaire, et l'exiger
        // ligne par ligne alourdirait le cas courant pour servir le cas rare.
        Map<Long, BigDecimal> aServir = quantitesAServir(lignesCommande, partiel, idCommandeClient);
        if (aServir.isEmpty()) {
            throw new InvalidEntityException("Cette commande n'a plus rien à servir",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID);
        }

        // La vente est celle du magasin qui a pris la commande ; la marchandise part du site qui la
        // livre, et c'est son equipe qui la sert : elle doit y travailler.
        Site expedition = commande.getSiteExpedition() != null ? commande.getSiteExpedition() : commande.getSite();
        if (expedition != null) {
            siteCourant.accessible(expedition.getId());
        }

        Vente vente = new Vente();
        vente.setCode(commande.getCode());
        vente.setDatevente(Instant.now());
        vente.setIdEntreprise(commande.getIdEntreprise());
        vente.setCommandeClient(commande);
        vente.setSite(commande.getSite());
        vente.setSiteExpedition(expedition);
        // Le client de la commande devient celui de la vente : la lecture n'a ensuite qu'un seul
        // chemin a suivre, que la vente vienne du comptoir ou d'une commande.
        vente.setClient(commande.getClient());
        // Son ticket rapporte des points comme un autre : il porte donc son code.
        vente.setCodeTicket(CodeTicket.nouveau());
        Vente enregistree = venteRepository.save(vente);

        for (LigneCmndeClient ligneCommande : lignesCommande) {
            BigDecimal quantite = aServir.get(ligneCommande.getId());
            if (quantite == null || quantite.signum() <= 0) {
                continue;
            }
            LigneVente ligneVente = new LigneVente();
            ligneVente.setVente(enregistree);
            ligneVente.setArticles(ligneCommande.getArticles());
            ligneVente.setQuantite(quantite);
            ligneVente.setPrixUnitaire(ligneCommande.getPrixUnitaire());
            // La vente reprend le conditionnement de la commande, et la contenance qu'elle avait
            // figee : on livre les cartons qui ont ete commandes, pas ceux d'aujourd'hui.
            ligneVente.setConditionnement(ligneCommande.getConditionnement());
            ligneVente.setContenance(ligneCommande.getContenance());
            ligneVente.setIdEntreprise(commande.getIdEntreprise());
            ligneVenteRepository.save(ligneVente);

            mvtStkService.sortieStock(MvtStkDto.builder()
                    .article(ArticleDto.builder().Id(ligneCommande.getArticles().getId()).build())
                    .quantite(enStock(ligneVente))
                    .motif(MotifMvtStk.VENTE)
                    .idSite(idSite(expedition))
                    .idVente(enregistree.getId())
                    .build());

            ligneCommande.setQuantiteLivree(dejaServi(ligneCommande).add(quantite));
            ligneCmndeClientRepository.save(ligneCommande);
        }

        // L'etat est constate, pas declare : tout est parti, la commande est livree ; il reste
        // quelque chose, elle est partiellement livree. Le tout dans la meme transaction — si une
        // ligne manque de stock, ni la vente ni le changement d'etat ne subsistent.
        boolean toutServi = lignesCommande.stream().allMatch(ligne -> resteAServir(ligne).signum() <= 0);
        commande.setEtat(toutServi ? EtatCommande.LIVREE : EtatCommande.PARTIELLEMENT_LIVREE);
        commandeClientRepository.save(commande);

        log.info("Commande client {} servie par la vente {} ({})",
                idCommandeClient, enregistree.getId(), commande.getEtat());
        return VenteDto.fromEntity(enregistree);
    }

    /**
     * Ce qui part maintenant, ligne par ligne.
     *
     * Sans precision, tout le reliquat. Avec une liste, seulement ce qu'elle nomme — et jamais
     * plus que ce qui reste du : servir au-dela de la commande n'est pas une livraison, c'est une
     * commande a corriger.
     */
    private Map<Long, BigDecimal> quantitesAServir(List<LigneCmndeClient> lignes,
                                                   List<LigneReceptionDto> partiel,
                                                   Long idCommande) {
        Map<Long, BigDecimal> quantites = new HashMap<>();
        if (partiel == null || partiel.isEmpty()) {
            lignes.forEach(ligne -> {
                BigDecimal reste = resteAServir(ligne);
                if (reste.signum() > 0) {
                    quantites.put(ligne.getId(), reste);
                }
            });
            return quantites;
        }

        for (LigneReceptionDto demande : partiel) {
            LigneCmndeClient ligne = lignes.stream()
                    .filter(l -> l.getId().equals(demande.getIdLigne()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidEntityException(
                            "La ligne " + demande.getIdLigne() + " n'appartient pas à la commande " + idCommande,
                            ErrorCodes.LIGNE_COMMANDE_CLIENT_NOT_VALID));
            BigDecimal quantite = demande.getQuantite();
            if (quantite == null || quantite.signum() <= 0) {
                throw new InvalidEntityException("La quantité servie doit être strictement positive",
                        ErrorCodes.LIGNE_COMMANDE_CLIENT_NOT_VALID);
            }
            if (quantite.compareTo(resteAServir(ligne)) > 0) {
                throw new InvalidEntityException(
                        "La quantité servie dépasse ce qui reste dû : " + resteAServir(ligne)
                                + " attendus, " + quantite + " servis",
                        ErrorCodes.LIGNE_COMMANDE_CLIENT_NOT_VALID);
            }
            quantites.put(ligne.getId(), quantite);
        }
        return quantites;
    }

    private BigDecimal dejaServi(LigneCmndeClient ligne) {
        return ligne.getQuantiteLivree() == null ? BigDecimal.ZERO : ligne.getQuantiteLivree();
    }

    private BigDecimal resteAServir(LigneCmndeClient ligne) {
        BigDecimal commandee = ligne.getQuantite() == null ? BigDecimal.ZERO : ligne.getQuantite();
        return commandee.subtract(dejaServi(ligne)).max(BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public VenteDto annuler(Long idVente) {
        Vente vente = venteModifiable(idVente);

        // Chaque ligne retourne en magasin. Le motif distingue cette entree d'une livraison :
        // sans lui, l'historique d'un article montrerait deux entrees identiques dont l'une
        // n'a jamais rien fait venir du fournisseur.
        ligneVenteRepository.findAllByVenteId(idVente).forEach(ligne ->
                mvtStkService.entreeStock(MvtStkDto.builder()
                        .article(ArticleDto.builder().Id(ligne.getArticles().getId()).build())
                        .quantite(enStock(ligne))
                        .motif(MotifMvtStk.ANNULATION_VENTE)
                        // La marchandise rendue retourne la d'ou elle est partie.
                        .idSite(idSite(vente.getSiteExpedition()))
                        // Et dans les lots d'ou elle etait sortie.
                        .idVente(vente.getId())
                        .build()));

        // La vente reste en base : une recette encaissee puis rendue doit pouvoir se retrouver.
        vente.setAnnulee(true);
        return VenteDto.fromEntity(venteRepository.save(vente));
    }

    @Override
    @Transactional
    public LigneVenteDto modifierQuantite(Long idVente, Long idLigne, BigDecimal quantite) {
        venteModifiable(idVente);
        LigneVente ligne = ligne(idVente, idLigne);
        BigDecimal nouvelle = quantiteValide(quantite);
        Conditionnements.verifierFraction(ligne.getArticles(), ligne.getConditionnement(), nouvelle,
                ErrorCodes.LIGNE_VENTE_NOT_VALID);
        BigDecimal ancienne = ligne.getQuantite();

        // La marchandise est deja sortie : seule la difference se rattrape. Augmenter sort le
        // complement — et echoue si le magasin ne l'a pas, ce qui est bien le comportement
        // attendu ; diminuer remet la difference.
        int comparaison = nouvelle.compareTo(ancienne);
        if (comparaison > 0) {
            mvtStkService.sortieStock(mouvementDe(ligne, nouvelle.subtract(ancienne)));
        } else if (comparaison < 0) {
            mvtStkService.entreeStock(mouvementDe(ligne, ancienne.subtract(nouvelle)));
        }

        ligne.setQuantite(nouvelle);
        return LigneVenteDto.fromEntity(ligneVenteRepository.save(ligne));
    }

    @Override
    @Transactional
    public void retirerLigne(Long idVente, Long idLigne) {
        venteModifiable(idVente);
        LigneVente ligne = ligne(idVente, idLigne);
        mvtStkService.entreeStock(mouvementDe(ligne, ligne.getQuantite()));
        ligneVenteRepository.delete(ligne);
    }

    /** Le mouvement qui corrige `quantite` unites de la ligne — des cartons si elle est en cartons. */
    private MvtStkDto mouvementDe(LigneVente ligne, BigDecimal quantite) {
        return MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ligne.getArticles().getId()).build())
                .quantite(Conditionnements.enUnitesDeBase(quantite, ligne.getContenance()))
                .motif(MotifMvtStk.CORRECTION_VENTE)
                .idSite(idSite(ligne.getVente() == null ? null : ligne.getVente().getSiteExpedition()))
                .idVente(ligne.getVente() == null ? null : ligne.getVente().getId())
                .build();
    }

    private static Long idSite(Site site) {
        return site == null ? null : site.getId();
    }

    /** Ce que la ligne a fait sortir du stock, en unites de base. */
    private static BigDecimal enStock(LigneVente ligne) {
        return Conditionnements.enUnitesDeBase(ligne.getQuantite(), ligne.getContenance());
    }

    private Vente vente(Long id) {
        if (id == null) {
            throw new InvalidEntityException("Aucune vente ne peut être cherchée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        Vente vente = venteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
        cloisonnement.verifierAcces(vente.getIdEntreprise(), "vente", id);
        return vente;
    }

    /**
     * Une vente annulee a deja rendu sa marchandise : la corriger la rendrait une seconde fois.
     * Une vente facturee est figee pour une autre raison — la facture porte des montants remis au
     * client, et changer la vente sous elle la ferait mentir. Annuler la facture rouvre la vente.
     */
    private Vente venteModifiable(Long id) {
        Vente vente = vente(id);
        if (vente.isAnnulee()) {
            throw new InvalidEntityException("Une vente annulée ne se modifie plus",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("La vente " + id + " a déjà été annulée"));
        }
        factureRepository.findByVenteId(id)
                .filter(facture -> !facture.isAnnulee())
                .ifPresent(facture -> {
                    throw new InvalidEntityException("Une vente facturée ne se modifie plus",
                            ErrorCodes.VENTE_NOT_VALID,
                            List.of("La facture " + facture.getNumero()
                                    + " doit être annulée avant de reprendre cette vente"));
                });
        return vente;
    }

    private LigneVente ligne(Long idVente, Long idLigne) {
        LigneVente ligne = ligneVenteRepository.findById(idLigne)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune ligne avec l'identifiant " + idLigne + " n'a été trouvée",
                        ErrorCodes.LIGNE_VENTE_NOT_FOUND));
        if (ligne.getVente() == null || !idVente.equals(ligne.getVente().getId())) {
            throw new InvalidEntityException(
                    "La ligne " + idLigne + " n'appartient pas à la vente " + idVente,
                    ErrorCodes.LIGNE_VENTE_NOT_VALID);
        }
        return ligne;
    }

    private BigDecimal quantiteValide(BigDecimal quantite) {
        if (quantite == null || quantite.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEntityException(
                    "La quantité d'une ligne de vente doit être strictement positive",
                    ErrorCodes.LIGNE_VENTE_NOT_VALID);
        }
        return quantite;
    }

    @Override
    public VenteDto findById(Long id) {
        if (id == null) {
            log.error("Vente id is null");
            throw new InvalidEntityException("Aucune vente ne peut être cherchée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return venteRepository.findById(id)
                .map(VenteDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
    }

    @Override
    public List<VenteDto> findAll() {
        return (cloisonnement.filtre()
                ? venteRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante())
                : venteRepository.findAll()).stream()
                .map(VenteDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<VenteDto> findAll(Pageable pageable) {
        return (cloisonnement.filtre()
                ? venteRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante(), pageable)
                : venteRepository.findAll(pageable))
                .map(VenteDto::fromEntity);
    }

    @Override
    public VenteDto findVenteByCode(String codeVente) {
        if (!StringUtils.hasLength(codeVente)) {
            log.error("Le code Vente est vide");
            throw new InvalidEntityException("Aucune vente ne peut être cherchée sans code",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return (cloisonnement.filtre()
                ? venteRepository.findVenteByCodeAndIdEntreprise(codeVente, cloisonnement.entrepriseCourante())
                : venteRepository.findVenteByCode(codeVente))
                .map(VenteDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec le code " + codeVente + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == null) {
            log.error("Vente Id est null");
            return;
        }
        venteRepository.deleteById(id);
    }
}
