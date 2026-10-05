package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.conditionnement.Conditionnements;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeNotification;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleSiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.SiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.dto.StockSiteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ArticleSite;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Lot;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeDate;
import com.jumpy.tech.gestionstock.gestiondestock.lot.Lots;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LotRepository;
import org.springframework.util.StringUtils;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import com.jumpy.tech.gestionstock.gestiondestock.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MvtStkServiceImpl implements MvtStkService {

    /** Qui doit savoir qu'un article s'epuise : ceux qui commandent et ceux qui rangent. */
    private static final List<ERole> ROLES_ALERTES_STOCK =
            List.of(ERole.ROLE_ADMIN, ERole.ROLE_MANAGER, ERole.ROLE_MAGASINIER);

    private final MvtStkRepository mvtStkRepository;
    private final ArticleRepository articleRepository;
    private final Cloisonnement cloisonnement;
    private final NotificationService notifications;
    private final Conditionnements conditionnements;
    private final SiteCourant siteCourant;
    private final SiteRepository siteRepository;
    private final ArticleSiteRepository articleSiteRepository;
    private final Lots lots;
    private final LotRepository lotRepository;
    private final Calendrier calendrier;
    private final com.jumpy.tech.gestionstock.gestiondestock.reservation.Reservations reservations;

    public MvtStkServiceImpl(MvtStkRepository mvtStkRepository, ArticleRepository articleRepository,
                             Cloisonnement cloisonnement, NotificationService notifications,
                             Conditionnements conditionnements, SiteCourant siteCourant,
                             SiteRepository siteRepository, ArticleSiteRepository articleSiteRepository,
                             Lots lots, LotRepository lotRepository, Calendrier calendrier,
                             com.jumpy.tech.gestionstock.gestiondestock.reservation.Reservations reservations) {
        this.reservations = reservations;
        this.lots = lots;
        this.lotRepository = lotRepository;
        this.calendrier = calendrier;
        this.conditionnements = conditionnements;
        this.siteCourant = siteCourant;
        this.siteRepository = siteRepository;
        this.articleSiteRepository = articleSiteRepository;
        this.mvtStkRepository = mvtStkRepository;
        this.articleRepository = articleRepository;
        this.cloisonnement = cloisonnement;
        this.notifications = notifications;
    }

    @Override
    public BigDecimal stockReelArticle(Long idArticle) {
        Article article = article(idArticle);
        return stockReel(article.getId(), siteCourant.site());
    }

    @Override
    public BigDecimal stockReelDansSite(Long idArticle, Long idSite) {
        Article article = article(idArticle);
        return stockReel(article.getId(), siteDuMouvement(article, idSite));
    }

    /**
     * Le stock dans chaque site de l'entreprise, y compris ceux qui n'en ont pas : on cherche
     * aussi ou la marchandise manque. Limite aux sites que l'appelant peut voir.
     */
    @Override
    public List<StockSiteDto> stocksParSite(Long idArticle) {
        Article article = article(idArticle);
        Map<Long, BigDecimal> parSite = mvtStkRepository
                .stocksParSite(List.of(article.getId()), TypeMvtStk.ENTREE).stream()
                .collect(Collectors.toMap(l -> (Long) l[1], l -> (BigDecimal) l[2]));
        if (article.getIdEntreprise() == null) {
            return List.of();
        }
        return siteRepository.findAllByIdEntrepriseOrderByPrincipalDescNomAsc(article.getIdEntreprise()).stream()
                .filter(s -> s.isActif() || parSite.containsKey(s.getId()))
                .filter(siteCourant::peutVoir)
                .map(s -> new StockSiteDto(s.getId(), s.getNom(), s.getType(),
                        parSite.getOrDefault(s.getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    @Override
    public List<MvtStkDto> mvtStkArticle(Long idArticle) {
        article(idArticle);
        return mvtStkRepository.findAllByArticlesIdOrderByDateMvtDesc(idArticle).stream()
                .map(MvtStkDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MvtStkDto entreeStock(MvtStkDto dto) {
        return enregistrer(dto, TypeMvtStk.ENTREE);
    }

    @Override
    @Transactional
    public MvtStkDto sortieStock(MvtStkDto dto) {
        return enregistrer(dto, TypeMvtStk.SORTIE);
    }

    /**
     * Une sortie qui a deja eu lieu ailleurs. Datee de ce moment-la, et non opposable au stock :
     * la marchandise est partie, refuser n'effacerait que la trace de ce qui a eu lieu.
     */
    @Override
    @Transactional
    public MvtStkDto sortieConstatee(MvtStkDto dto, Instant quand) {
        if (quand == null) {
            throw new InvalidEntityException(
                    "Une sortie constatée porte la date à laquelle elle a eu lieu",
                    ErrorCodes.MVT_STK_NOT_VALID);
        }
        return enregistrer(dto, TypeMvtStk.SORTIE, quand, false);
    }

    @Override
    @Transactional
    public MvtStkDto corrigerAuComptage(Long idArticle, BigDecimal ecart) {
        return corrigerAuComptage(idArticle, ecart, null);
    }

    @Override
    @Transactional
    public MvtStkDto corrigerAuComptage(Long idArticle, BigDecimal ecart, Long idSite) {
        if (ecart == null || ecart.signum() == 0) {
            return null;
        }
        MvtStkDto dto = MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(ecart.abs())
                .motif(MotifMvtStk.INVENTAIRE)
                .idSite(idSite)
                .build();
        // `false` : un rattrapage ne s'oppose pas au stock. Si le logiciel croit avoir trois
        // unites et qu'on n'en trouve aucune, il faut bien en sortir trois d'un stock qui, sur
        // l'etagere, n'existe pas.
        return enregistrer(dto, ecart.signum() > 0 ? TypeMvtStk.ENTREE : TypeMvtStk.SORTIE,
                Instant.now(), false);
    }

    /**
     * Le sens du mouvement vient de la methode appelee, jamais du DTO : un client qui poste une
     * sortie en la marquant « entree » augmenterait le stock au lieu de le diminuer.
     */
    private MvtStkDto enregistrer(MvtStkDto dto, TypeMvtStk sens) {
        return enregistrer(dto, sens, Instant.now(), true);
    }

    private MvtStkDto enregistrer(MvtStkDto dto, TypeMvtStk sens, Instant quand, boolean opposerLeStock) {
        if (dto == null || dto.getArticle() == null || dto.getArticle().getId() == null) {
            throw new InvalidEntityException("Un mouvement de stock désigne un article",
                    ErrorCodes.MVT_STK_NOT_VALID);
        }
        Article article = article(dto.getArticle().getId());
        BigDecimal quantite = quantiteValide(dto.getQuantite());
        if (dto.getConditionnement() != null && dto.getConditionnement().getId() != null) {
            Conditionnement conditionnement = conditionnements.pourLigneConstatee(article, dto.getConditionnement());
            Conditionnements.verifierFraction(article, conditionnement, quantite, ErrorCodes.MVT_STK_NOT_VALID);
            quantite = Conditionnements.enUnitesDeBase(quantite, Conditionnements.contenance(conditionnement));
        }

        Site site = siteDuMouvement(article, dto.getIdSite());
        if (sens == TypeMvtStk.SORTIE && opposerLeStock && site != null && respecteLesReservations(dto.getMotif())) {
            verifierHorsReservations(article, site, quantite, dto.getIdCommandeClient());
        }
        List<String> avertissements = new java.util.ArrayList<>();

        // Un article suivi bouge lot par lot. Une sortie sans lot designe se repartit sur les lots
        // du site, premier perime premier sorti ; elle peut donc faire plusieurs mouvements.
        List<Lots.Part> parts;
        if (!article.isSuiviLot() || site == null) {
            if (sens == TypeMvtStk.SORTIE && opposerLeStock) {
                verifierStockDisponible(article, site, quantite);
            }
            parts = List.of(new Lots.Part(null, quantite));
        } else if (sens == TypeMvtStk.ENTREE && dto.getIdLot() == null && !StringUtils.hasText(dto.getNumeroLot())
                && (dto.getIdVente() != null || dto.getIdTransfert() != null)) {
            // Ce qui revient d'une vente, ou arrive d'un transfert, rentre dans les lots dont il
            // etait sorti : un lot rappele doit se retrouver jusque dans le stock d'arrivee.
            parts = rendre(dto.getIdVente() != null
                    ? mvtStkRepository.netParLotDeLaVente(dto.getIdVente(), article.getId(), TypeMvtStk.SORTIE)
                    : mvtStkRepository.netParLotDuTransfert(dto.getIdTransfert(), article.getId(), TypeMvtStk.SORTIE),
                    quantite);
        } else if (sens == TypeMvtStk.ENTREE) {
            parts = List.of(new Lots.Part(lotDEntree(article, dto), quantite));
        } else if (dto.getIdLot() != null) {
            Lot lot = lotDeLArticle(article, dto.getIdLot());
            if (opposerLeStock) {
                BigDecimal duLot = lots.stockDuLot(article, site, lot);
                if (duLot.compareTo(quantite) < 0) {
                    throw new InvalidEntityException(
                            "Le lot " + lot.getNumero() + " n'a plus que " + duLot.stripTrailingZeros().toPlainString()
                                    + " à « " + site.getNom() + " », " + quantite.stripTrailingZeros().toPlainString() + " demandés",
                            ErrorCodes.STOCK_INSUFFISANT);
                }
                if (lot.perimeLe(calendrier.aujourdhui()) && article.getTypeDate() == TypeDate.DLC
                        && dto.getMotif() != MotifMvtStk.PEREMPTION && dto.getMotif() != MotifMvtStk.RETOUR_FOURNISSEUR) {
                    throw new InvalidEntityException(
                            "Le lot " + lot.getNumero() + " a dépassé sa date limite : il ne se vend plus",
                            ErrorCodes.STOCK_INSUFFISANT, List.of("Sortez-le du stock avec le motif « Péremption »"));
                }
            }
            parts = List.of(new Lots.Part(lot, quantite));
        } else {
            parts = lots.allouer(article, site, quantite, opposerLeStock, avertissements);
        }

        MvtStk enregistre = null;
        for (Lots.Part part : parts) {
            enregistre = ecrire(article, site, part.lot(), part.quantite(), sens, dto, quand);
        }
        log.info("Mouvement {} de {} sur l'article {} (site {}, {} lot(s))", sens, quantite, article.getId(),
                site == null ? "-" : site.getId(), parts.size());

        if (sens == TypeMvtStk.SORTIE) {
            alerterSiLeStockBaisseTrop(article, site);
        }
        MvtStkDto resultat = MvtStkDto.fromEntity(enregistre);
        resultat.setAvertissements(avertissements);
        return resultat;
    }

    /**
     * Repartit une entree sur les lots d'ou la marchandise etait sortie : `net` donne, lot par lot,
     * ce qui est parti et n'est pas encore revenu. Les lots qui perissent le plus tard reviennent
     * d'abord — c'est eux qui restent vendables le plus longtemps. Ce qui depasse, s'il y en a,
     * entre sans lot.
     */
    private List<Lots.Part> rendre(List<Object[]> net, BigDecimal quantite) {
        Map<Long, BigDecimal> parLot = new java.util.HashMap<>();
        BigDecimal sansLot = BigDecimal.ZERO;
        for (Object[] ligne : net) {
            BigDecimal q = (BigDecimal) ligne[1];
            if (q.signum() <= 0) {
                continue;
            }
            if (ligne[0] == null) {
                sansLot = q;
            } else {
                parLot.put((Long) ligne[0], q);
            }
        }
        List<Lot> ordre = lotRepository.findAllById(parLot.keySet()).stream()
                .sorted(java.util.Comparator.comparing(Lot::getDatePeremption,
                        java.util.Comparator.nullsFirst(java.util.Comparator.<java.time.LocalDate>naturalOrder()))
                        .reversed())
                .toList();
        List<Lots.Part> parts = new java.util.ArrayList<>();
        BigDecimal reste = quantite;
        for (Lot lot : ordre) {
            if (reste.signum() <= 0) {
                break;
            }
            BigDecimal pris = reste.min(parLot.get(lot.getId()));
            parts.add(new Lots.Part(lot, pris));
            reste = reste.subtract(pris);
        }
        if (reste.signum() > 0) {
            parts.add(new Lots.Part(null, reste));
        }
        return parts;
    }

    /** Le lot d'une entree : designe par son identifiant, ou par son numero et sa date. */
    private Lot lotDEntree(Article article, MvtStkDto dto) {
        if (dto.getIdLot() != null) {
            return lotDeLArticle(article, dto.getIdLot());
        }
        if (StringUtils.hasText(dto.getNumeroLot())) {
            return lots.pourEntree(article, dto.getNumeroLot(), dto.getDatePeremption());
        }
        // Une livraison et une saisie a la main disent d'ou vient la marchandise : son lot est
        // sur l'emballage. Un rattrapage d'inventaire, un retour de client, ne le savent pas
        // toujours : la marchandise entre sans lot, et sortira la premiere.
        MotifMvtStk motif = dto.getMotif() == null ? MotifMvtStk.SAISIE_MANUELLE : dto.getMotif();
        if (motif == MotifMvtStk.LIVRAISON_COMMANDE || motif == MotifMvtStk.SAISIE_MANUELLE) {
            lots.pourEntree(article, null, null);
        }
        return null;
    }

    private Lot lotDeLArticle(Article article, Long idLot) {
        return lotRepository.findById(idLot)
                .filter(l -> l.getArticle().getId().equals(article.getId()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun lot " + idLot + " pour l'article " + article.getCodeArticle(), ErrorCodes.MVT_STK_NOT_VALID));
    }

    private MvtStk ecrire(Article article, Site site, Lot lot, BigDecimal quantite, TypeMvtStk sens, MvtStkDto dto,
                          Instant quand) {
        MvtStk mvtStk = new MvtStk();
        mvtStk.setArticles(article);
        mvtStk.setSite(site);
        mvtStk.setLot(lot);
        mvtStk.setIdVente(dto.getIdVente());
        mvtStk.setIdTransfert(dto.getIdTransfert());
        mvtStk.setQuantite(quantite);
        mvtStk.setTypMvt(sens);
        // Un mouvement sans motif connu est une saisie a la main : c'est le cas des deux routes
        // publiques, ou personne ne peut dire quel document porte le mouvement.
        mvtStk.setMotif(dto.getMotif() == null ? MotifMvtStk.SAISIE_MANUELLE : dto.getMotif());
        // Le mouvement herite de l'entreprise de l'article, pas de ce que dit la requete :
        // l'article vient d'etre verifie, il fait donc foi.
        mvtStk.setIdEntreprise(article.getIdEntreprise());
        // Un mouvement est date du moment ou il a lieu. Les deux routes publiques passent par
        // `enregistrer(dto, sens)`, qui impose l'heure du serveur : laisser un appelant
        // quelconque antidater une sortie permettrait de fabriquer un stock qui n'a jamais
        // existe. Seule une sortie constatee — une vente faite hors ligne, deja survenue — porte
        // sa propre date, et elle ne vient pas d'une requete mais du service des ventes.
        mvtStk.setDateMvt(quand);
        return mvtStkRepository.save(mvtStk);
    }

    /**
     * Previent le magasin quand un article s'epuise.
     *
     * Seulement sur une sortie : une entree ne fait jamais baisser le stock, et verifier apres
     * chaque reception couterait une requete pour rien.
     *
     * La rupture se decouvrait au comptoir, devant le client. Trois situations, trois messages,
     * parce qu'elles n'appellent pas le meme geste — un negatif se compte sur l'etagere, une
     * rupture et un sous-seuil se commandent au fournisseur.
     */
    private void alerterSiLeStockBaisseTrop(Article article, Site site) {
        BigDecimal restant = stockReel(article.getId(), site);
        BigDecimal seuil = seuil(article, site);
        // Le nom du site, seulement quand il y en a plusieurs : « au Dépôt Bonabéri » dit ou aller
        // compter, et un commerce a un seul magasin n'a pas besoin qu'on le lui rappelle.
        String ou = site != null && siteRepository.countByIdEntrepriseAndActifTrue(article.getIdEntreprise()) > 1
                ? " — " + site.getNom() : "";

        String titre;
        String corps;
        // La gravite entre dans la cle : sans elle, une alerte « sous le seuil » non lue
        // masquerait l'aggravation vers la rupture, puis vers le negatif. Le magasinier lirait
        // « il reste 8 » sur un article deja tombe a zero — et les trois situations n'appellent
        // justement pas le meme geste.
        String gravite;
        if (restant.signum() < 0) {
            gravite = "negatif";
            // Pas une erreur a masquer : la marchandise est partie. C'est le signal qu'un
            // inventaire est a faire, et il se lit aussi sur /stock/alertes.
            log.warn("Stock negatif sur l'article {} : {}", article.getCodeArticle(), restant);
            titre = "Stock négatif : " + article.getDesignation() + ou;
            corps = "Il est sorti plus de « " + article.getDesignation() + " » (" + article.getCodeArticle()
                    + ") que le magasin n'en avait reçu : " + restant + " en stock. "
                    + "Un comptage sur l'étagère est nécessaire.";
        } else if (restant.signum() == 0) {
            gravite = "rupture";
            titre = "Rupture : " + article.getDesignation() + ou;
            corps = "« " + article.getDesignation() + " » (" + article.getCodeArticle()
                    + ") est épuisé.";
        } else if (seuil != null && restant.compareTo(seuil) <= 0) {
            gravite = "sous-seuil";
            titre = "Sous le seuil : " + article.getDesignation() + ou;
            corps = "Il reste " + restant + " « " + article.getDesignation() + " » ("
                    + article.getCodeArticle() + "), pour un seuil d'alerte de " + seuil + ".";
        } else {
            return;
        }

        notifications.prevenirLesRoles(
                article.getIdEntreprise(), ROLES_ALERTES_STOCK, TypeNotification.STOCK_ALERTE,
                titre, corps, "/stock/alertes",
                // La cle porte l'article et la gravite : tant qu'une alerte de meme nature n'est
                // pas lue, les ventes suivantes n'en ecrivent pas d'autre — sans quoi une journee
                // de comptoir enterrerait la boite aux lettres sous le meme message. Une
                // aggravation, elle, passe : c'est une nouvelle information.
                "stock:" + article.getId() + ":" + (site == null ? "" : site.getId() + ":") + gravite);
    }

    /** Le seuil de l'article dans ce site, a defaut celui de l'article. */
    private BigDecimal seuil(Article article, Site site) {
        if (site == null) {
            return article.getSeuilAlerte();
        }
        return articleSiteRepository.findByArticleIdAndSiteId(article.getId(), site.getId())
                .map(ArticleSite::getSeuilAlerte)
                .filter(Objects::nonNull)
                .orElse(article.getSeuilAlerte());
    }

    /**
     * Le site d'un mouvement : celui que designe l'appelant interne — la vente, la reception —,
     * a defaut le site actif. Il est toujours de l'entreprise de l'article : un mouvement ne fait
     * pas passer de la marchandise chez le voisin.
     */
    private Site siteDuMouvement(Article article, Long idSite) {
        if (article.getIdEntreprise() == null) {
            return null;
        }
        if (idSite == null) {
            Site actif = siteCourant.site();
            return actif != null ? actif : siteCourant.principal(article.getIdEntreprise());
        }
        return siteRepository.findById(idSite)
                .filter(s -> Objects.equals(s.getIdEntreprise(), article.getIdEntreprise()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun site avec l'identifiant " + idSite + " n'a été trouvé", ErrorCodes.SITE_NOT_FOUND));
    }

    /**
     * Quantite toujours positive : c'est le sens du mouvement qui porte le signe. Une sortie de
     * -5 enregistree telle quelle augmenterait le stock, par la seule vertu de deux signes qui
     * s'annulent.
     */
    private BigDecimal quantiteValide(BigDecimal quantite) {
        if (quantite == null || quantite.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEntityException(
                    "La quantité d'un mouvement de stock doit être strictement positive",
                    ErrorCodes.MVT_STK_NOT_VALID);
        }
        return quantite;
    }

    /**
     * Les sorties qui ne puisent que dans le disponible : vendre, charger un camion, prendre pour
     * soi. Une casse, une peremption, un vol sont des faits — la marchandise n'est plus la, que des
     * commandes l'attendent ou non.
     */
    private static boolean respecteLesReservations(MotifMvtStk motif) {
        return motif == MotifMvtStk.VENTE || motif == MotifMvtStk.TRANSFERT_SORTIE
                || motif == MotifMvtStk.CONSOMMATION_INTERNE;
    }

    /** Ce que les commandes des autres retiennent n'est pas a vendre. */
    private void verifierHorsReservations(Article article, Site site, BigDecimal quantite, Long commandeServie) {
        BigDecimal reserve = reservations.dansSite(article.getId(), site.getId(), commandeServie);
        if (reserve.signum() <= 0) {
            return;
        }
        BigDecimal enStock = stockReel(article.getId(), site);
        BigDecimal disponible = enStock.subtract(reserve).max(BigDecimal.ZERO);
        if (disponible.compareTo(quantite) < 0) {
            throw new InvalidEntityException(
                    "Stock insuffisant pour l'article " + article.getCodeArticle() + " à « " + site.getNom() + " » : "
                            + disponible.stripTrailingZeros().toPlainString() + " disponibles, "
                            + quantite.stripTrailingZeros().toPlainString() + " demandés",
                    ErrorCodes.STOCK_INSUFFISANT,
                    List.of(enStock.stripTrailingZeros().toPlainString() + " en stock, dont "
                            + reserve.stripTrailingZeros().toPlainString() + " réservés pour des commandes clients"));
        }
    }

    private void verifierStockDisponible(Article article, Site site, BigDecimal quantite) {
        BigDecimal disponible = stockReel(article.getId(), site);
        if (disponible.compareTo(quantite) < 0) {
            throw new InvalidEntityException(
                    "Stock insuffisant pour l'article " + article.getCodeArticle()
                            + " : " + disponible + (site == null ? " en magasin" : " à « " + site.getNom() + " »")
                            + ", " + quantite + " demandés",
                    ErrorCodes.STOCK_INSUFFISANT,
                    List.of("Article " + article.getCodeArticle() + " : stock réel " + disponible
                            + ", quantité demandée " + quantite));
        }
    }

    /** Le stock d'un article dans un site ; dans tous, sans site. */
    private BigDecimal stockReel(Long idArticle, Site site) {
        if (site == null) {
            BigDecimal entrees = mvtStkRepository.sommeParType(idArticle, TypeMvtStk.ENTREE);
            BigDecimal sorties = mvtStkRepository.sommeParType(idArticle, TypeMvtStk.SORTIE);
            return entrees.subtract(sorties);
        }
        BigDecimal entrees = mvtStkRepository.sommeParTypeDansSite(idArticle, site.getId(), TypeMvtStk.ENTREE);
        BigDecimal sorties = mvtStkRepository.sommeParTypeDansSite(idArticle, site.getId(), TypeMvtStk.SORTIE);
        return entrees.subtract(sorties);
    }

    private Article article(Long idArticle) {
        if (idArticle == null) {
            throw new InvalidEntityException("Aucun article ne peut être cherché sans identifiant",
                    ErrorCodes.ARTICLE_NOT_VALID);
        }
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + idArticle + " n'a été trouvé",
                        ErrorCodes.ARTICLE_NOT_FOUND));
        // Le cloisonnement des mouvements passe par l'article : c'est lui qui porte l'entreprise,
        // et consulter le stock d'un article qui n'est pas le sien reviendrait a lire l'activite
        // du voisin.
        cloisonnement.verifierAcces(article.getIdEntreprise(), "article", idArticle);
        return article;
    }
}
