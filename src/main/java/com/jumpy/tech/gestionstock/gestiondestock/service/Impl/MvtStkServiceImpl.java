package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
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
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import com.jumpy.tech.gestionstock.gestiondestock.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    public MvtStkServiceImpl(MvtStkRepository mvtStkRepository, ArticleRepository articleRepository,
                             Cloisonnement cloisonnement, NotificationService notifications) {
        this.mvtStkRepository = mvtStkRepository;
        this.articleRepository = articleRepository;
        this.cloisonnement = cloisonnement;
        this.notifications = notifications;
    }

    @Override
    public BigDecimal stockReelArticle(Long idArticle) {
        Article article = article(idArticle);
        return stockReel(article.getId());
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
        if (ecart == null || ecart.signum() == 0) {
            return null;
        }
        MvtStkDto dto = MvtStkDto.builder()
                .article(ArticleDto.builder().Id(idArticle).build())
                .quantite(ecart.abs())
                .motif(MotifMvtStk.INVENTAIRE)
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

        if (sens == TypeMvtStk.SORTIE && opposerLeStock) {
            verifierStockDisponible(article, quantite);
        }

        MvtStk mvtStk = new MvtStk();
        mvtStk.setArticles(article);
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

        MvtStk enregistre = mvtStkRepository.save(mvtStk);
        log.info("Mouvement {} de {} sur l'article {}", sens, quantite, article.getId());

        if (sens == TypeMvtStk.SORTIE) {
            alerterSiLeStockBaisseTrop(article);
        }
        return MvtStkDto.fromEntity(enregistre);
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
    private void alerterSiLeStockBaisseTrop(Article article) {
        BigDecimal restant = stockReel(article.getId());
        BigDecimal seuil = article.getSeuilAlerte();

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
            titre = "Stock négatif : " + article.getDesignation();
            corps = "Il est sorti plus de « " + article.getDesignation() + " » (" + article.getCodeArticle()
                    + ") que le magasin n'en avait reçu : " + restant + " en stock. "
                    + "Un comptage sur l'étagère est nécessaire.";
        } else if (restant.signum() == 0) {
            gravite = "rupture";
            titre = "Rupture : " + article.getDesignation();
            corps = "« " + article.getDesignation() + " » (" + article.getCodeArticle()
                    + ") est épuisé.";
        } else if (seuil != null && restant.compareTo(seuil) <= 0) {
            gravite = "sous-seuil";
            titre = "Sous le seuil : " + article.getDesignation();
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
                "stock:" + article.getId() + ":" + gravite);
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

    private void verifierStockDisponible(Article article, BigDecimal quantite) {
        BigDecimal disponible = stockReel(article.getId());
        if (disponible.compareTo(quantite) < 0) {
            throw new InvalidEntityException(
                    "Stock insuffisant pour l'article " + article.getCodeArticle()
                            + " : " + disponible + " en magasin, " + quantite + " demandés",
                    ErrorCodes.STOCK_INSUFFISANT,
                    List.of("Article " + article.getCodeArticle() + " : stock réel " + disponible
                            + ", quantité demandée " + quantite));
        }
    }

    private BigDecimal stockReel(Long idArticle) {
        BigDecimal entrees = mvtStkRepository.sommeParType(idArticle, TypeMvtStk.ENTREE);
        BigDecimal sorties = mvtStkRepository.sommeParType(idArticle, TypeMvtStk.SORTIE);
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
