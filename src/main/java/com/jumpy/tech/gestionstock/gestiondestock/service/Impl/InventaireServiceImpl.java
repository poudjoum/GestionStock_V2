package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneComptageDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SeanceInventaireDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneComptage;
import com.jumpy.tech.gestionstock.gestiondestock.entities.SeanceInventaire;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutSeanceInventaire;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneComptageRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.SeanceInventaireRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.InventaireService;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class InventaireServiceImpl implements InventaireService {

    /** Les trois vues de la liste des lignes. */
    private static final Set<String> VUES = Set.of("TOUTES", "A_COMPTER", "ECARTS");

    private final SeanceInventaireRepository seanceRepository;
    private final LigneComptageRepository ligneRepository;
    private final ArticleRepository articleRepository;
    private final MvtStkRepository mvtStkRepository;
    private final MvtStkService mvtStkService;
    private final Cloisonnement cloisonnement;

    public InventaireServiceImpl(SeanceInventaireRepository seanceRepository,
                                 LigneComptageRepository ligneRepository,
                                 ArticleRepository articleRepository,
                                 MvtStkRepository mvtStkRepository,
                                 MvtStkService mvtStkService,
                                 Cloisonnement cloisonnement) {
        this.seanceRepository = seanceRepository;
        this.ligneRepository = ligneRepository;
        this.articleRepository = articleRepository;
        this.mvtStkRepository = mvtStkRepository;
        this.mvtStkService = mvtStkService;
        this.cloisonnement = cloisonnement;
    }

    @Override
    @Transactional
    public SeanceInventaireDto ouvrir(String commentaire) {
        Long entreprise = cloisonnement.entrepriseCourante();
        seanceRepository.findByStatutAndIdEntreprise(StatutSeanceInventaire.OUVERTE, entreprise)
                .ifPresent(ouverte -> {
                    throw new InvalidEntityException(
                            "Une séance d'inventaire est déjà ouverte (" + ouverte.getReference()
                                    + ") : validez-la ou abandonnez-la avant d'en ouvrir une autre",
                            ErrorCodes.INVENTAIRE_NOT_VALID);
                });

        List<Article> articles = cloisonnement.filtre()
                ? articleRepository.findAllByIdEntreprise(entreprise)
                : articleRepository.findAll();
        if (articles.isEmpty()) {
            throw new InvalidEntityException(
                    "Le catalogue est vide : il n'y a rien à compter",
                    ErrorCodes.INVENTAIRE_NOT_VALID);
        }

        SeanceInventaire seance = new SeanceInventaire();
        seance.setReference(referenceSuivante());
        seance.setDateOuverture(Instant.now());
        seance.setStatut(StatutSeanceInventaire.OUVERTE);
        seance.setCommentaire(commentaire);
        seance.setIdEntreprise(entreprise);
        SeanceInventaire enregistree = seanceRepository.save(seance);

        // Le stock de tout le catalogue en une requete. Le demander article par article en ferait
        // deux par ligne — un inventaire de mille references en produirait deux mille.
        Map<Long, BigDecimal> stocks = stocksDe(articles);

        List<LigneComptage> lignes = new ArrayList<>(articles.size());
        for (Article article : articles) {
            LigneComptage ligne = new LigneComptage();
            ligne.setSeance(enregistree);
            ligne.setArticle(article);
            ligne.setCodeArticle(article.getCodeArticle());
            ligne.setDesignation(article.getDesignation());
            ligne.setQuantiteTheorique(stocks.getOrDefault(article.getId(), BigDecimal.ZERO));
            ligne.setIdEntreprise(article.getIdEntreprise());
            lignes.add(ligne);
        }
        ligneRepository.saveAll(lignes);

        log.info("Séance d'inventaire {} ouverte sur {} articles",
                enregistree.getReference(), lignes.size());
        return SeanceInventaireDto.fromEntity(enregistree, lignes.size(), 0, 0);
    }

    @Override
    public SeanceInventaireDto seanceOuverte() {
        return seanceRepository
                .findByStatutAndIdEntreprise(StatutSeanceInventaire.OUVERTE,
                        cloisonnement.entrepriseCourante())
                .map(this::avecSesCompteurs)
                .orElse(null);
    }

    @Override
    public SeanceInventaireDto seance(Long idSeance) {
        return avecSesCompteurs(chargerLaSeance(idSeance));
    }

    @Override
    public Page<LigneComptageDto> lignes(Long idSeance, String q, String vue, Pageable pageable) {
        chargerLaSeance(idSeance);
        String demandee = vue == null || vue.isBlank() ? "TOUTES" : vue.toUpperCase();
        if (!VUES.contains(demandee)) {
            throw new InvalidEntityException(
                    "Vue inconnue : " + vue + ". Les vues possibles sont TOUTES, A_COMPTER et ECARTS",
                    ErrorCodes.INVENTAIRE_NOT_VALID);
        }
        return ligneRepository
                .rechercher(idSeance, RechercheUtils.normaliser(q), demandee, pageable)
                .map(LigneComptageDto::fromEntity);
    }

    @Override
    @Transactional
    public LigneComptageDto compter(Long idSeance, Long idArticle, BigDecimal quantite) {
        SeanceInventaire seance = seanceOuverteOuErreur(idSeance);
        LigneComptage ligne = ligneRepository.findBySeanceIdAndArticleId(idSeance, idArticle)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Cet article ne fait pas partie de la séance " + seance.getReference()
                                + " : il a été créé après son ouverture",
                        ErrorCodes.INVENTAIRE_NOT_FOUND));
        return noter(ligne, quantite);
    }

    @Override
    @Transactional
    public LigneComptageDto compterParCode(Long idSeance, String codeArticle, BigDecimal quantite) {
        SeanceInventaire seance = seanceOuverteOuErreur(idSeance);
        if (codeArticle == null || codeArticle.isBlank()) {
            throw new InvalidEntityException("Aucun code n'a été lu", ErrorCodes.INVENTAIRE_NOT_VALID);
        }
        Article article = (cloisonnement.filtre()
                ? articleRepository.findArticleByCodeArticleAndIdEntreprise(
                        codeArticle.trim(), cloisonnement.entrepriseCourante())
                : articleRepository.findArticleByCodeArticle(codeArticle.trim()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article ne porte le code " + codeArticle,
                        ErrorCodes.ARTICLE_NOT_FOUND));
        LigneComptage ligne = ligneRepository.findBySeanceIdAndArticleId(idSeance, article.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "L'article " + codeArticle + " ne fait pas partie de la séance "
                                + seance.getReference() + " : il a été créé après son ouverture",
                        ErrorCodes.INVENTAIRE_NOT_FOUND));
        return noter(ligne, quantite);
    }

    @Override
    @Transactional
    public LigneComptageDto annulerComptage(Long idSeance, Long idLigne) {
        seanceOuverteOuErreur(idSeance);
        LigneComptage ligne = ligneRepository.findById(idLigne)
                .filter(l -> l.getSeance().getId().equals(idSeance))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune ligne de comptage avec l'identifiant " + idLigne,
                        ErrorCodes.INVENTAIRE_NOT_FOUND));
        // Les trois champs partent ensemble : la base impose qu'ils soient tous nuls ou tous
        // renseignes, et une ligne a demi comptee ne voudrait rien dire.
        ligne.setQuantiteComptee(null);
        ligne.setStockAuComptage(null);
        ligne.setCompteLe(null);
        return LigneComptageDto.fromEntity(ligneRepository.save(ligne));
    }

    @Override
    @Transactional
    public SeanceInventaireDto valider(Long idSeance) {
        SeanceInventaire seance = seanceOuverteOuErreur(idSeance);

        List<LigneComptage> aCorriger = ligneRepository.aCorriger(idSeance);
        for (LigneComptage ligne : aCorriger) {
            BigDecimal ecart = ligne.getQuantiteComptee().subtract(ligne.getStockAuComptage());
            mvtStkService.corrigerAuComptage(ligne.getArticle().getId(), ecart);
        }

        seance.setStatut(StatutSeanceInventaire.VALIDEE);
        seance.setDateCloture(Instant.now());
        SeanceInventaire close = seanceRepository.save(seance);

        long restees = ligneRepository.countBySeanceIdAndQuantiteCompteeIsNull(idSeance);
        log.info("Séance {} validée : {} correction(s), {} article(s) non comptés",
                close.getReference(), aCorriger.size(), restees);
        return avecSesCompteurs(close);
    }

    @Override
    @Transactional
    public SeanceInventaireDto abandonner(Long idSeance) {
        SeanceInventaire seance = seanceOuverteOuErreur(idSeance);
        seance.setStatut(StatutSeanceInventaire.ABANDONNEE);
        seance.setDateCloture(Instant.now());
        return avecSesCompteurs(seanceRepository.save(seance));
    }

    @Override
    public Page<SeanceInventaireDto> historique(Pageable pageable) {
        Page<SeanceInventaire> page = cloisonnement.filtre()
                ? seanceRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante(), pageable)
                : seanceRepository.findAll(pageable);
        return page.map(this::avecSesCompteurs);
    }

    /**
     * Note la quantite trouvee, et retient au passage le stock du logiciel a cet instant.
     *
     * C'est ce second nombre qui servira a corriger. L'ecart montre a l'ecran, lui, se mesure
     * contre le theorique fige a l'ouverture : c'est celui qu'on compare, et il ne doit pas
     * bouger pendant qu'on compte.
     */
    private LigneComptageDto noter(LigneComptage ligne, BigDecimal quantite) {
        if (quantite == null || quantite.signum() < 0) {
            throw new InvalidEntityException(
                    "La quantité comptée ne peut pas être négative",
                    ErrorCodes.INVENTAIRE_NOT_VALID);
        }
        ligne.setQuantiteComptee(quantite);
        ligne.setStockAuComptage(mvtStkService.stockReelArticle(ligne.getArticle().getId()));
        ligne.setCompteLe(Instant.now());
        return LigneComptageDto.fromEntity(ligneRepository.save(ligne));
    }

    private SeanceInventaire chargerLaSeance(Long idSeance) {
        if (idSeance == null) {
            throw new InvalidEntityException("Aucune séance ne peut être cherchée sans identifiant",
                    ErrorCodes.INVENTAIRE_NOT_VALID);
        }
        SeanceInventaire seance = seanceRepository.findById(idSeance)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune séance d'inventaire avec l'identifiant " + idSeance,
                        ErrorCodes.INVENTAIRE_NOT_FOUND));
        cloisonnement.verifierAcces(seance.getIdEntreprise(), "séance d'inventaire", idSeance);
        return seance;
    }

    private SeanceInventaire seanceOuverteOuErreur(Long idSeance) {
        SeanceInventaire seance = chargerLaSeance(idSeance);
        if (seance.getStatut() != StatutSeanceInventaire.OUVERTE) {
            throw new InvalidEntityException(
                    "La séance " + seance.getReference() + " est close : elle ne se modifie plus",
                    ErrorCodes.INVENTAIRE_NOT_VALID);
        }
        return seance;
    }

    private SeanceInventaireDto avecSesCompteurs(SeanceInventaire seance) {
        List<LigneComptage> lignes = ligneRepository.findAllBySeanceId(seance.getId());
        long comptes = lignes.stream().filter(l -> l.getQuantiteComptee() != null).count();
        long ecarts = lignes.stream()
                .filter(l -> l.getQuantiteComptee() != null)
                .filter(l -> l.getQuantiteComptee().compareTo(l.getQuantiteTheorique()) != 0)
                .count();
        return SeanceInventaireDto.fromEntity(seance, lignes.size(), comptes, ecarts);
    }

    private Map<Long, BigDecimal> stocksDe(List<Article> articles) {
        List<Long> ids = articles.stream().map(Article::getId).toList();
        Map<Long, BigDecimal> stocks = new HashMap<>();
        for (Object[] ligne : mvtStkRepository.stocksReels(ids, TypeMvtStk.ENTREE)) {
            stocks.put((Long) ligne[0], (BigDecimal) ligne[1]);
        }
        return stocks;
    }

    /** Format `INV-2026-000012` : l'annee se lit sans ouvrir la seance, le rang ne se rejoue pas. */
    private String referenceSuivante() {
        long rang = seanceRepository.prochaineReference();
        int annee = Instant.now().atZone(ZoneId.systemDefault()).getYear();
        return String.format("INV-%d-%06d", annee, rang);
    }
}
