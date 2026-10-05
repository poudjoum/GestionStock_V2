package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.conditionnement.Conditionnements;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReceptionTransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.TransfertDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatTransfert;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneTransfert;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Transfert;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneTransfertRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.SiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.TransfertRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import com.jumpy.tech.gestionstock.gestiondestock.service.TransfertService;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransfertServiceImpl implements TransfertService {

    private final TransfertRepository transfertRepository;
    private final LigneTransfertRepository ligneRepository;
    private final ArticleRepository articleRepository;
    private final SiteRepository siteRepository;
    private final MvtStkService mvtStkService;
    private final Conditionnements conditionnements;
    private final SiteCourant siteCourant;
    private final Cloisonnement cloisonnement;
    private final com.jumpy.tech.gestionstock.gestiondestock.service.ConditionnementService conditionnementService;

    public TransfertServiceImpl(TransfertRepository transfertRepository, LigneTransfertRepository ligneRepository,
                                ArticleRepository articleRepository, SiteRepository siteRepository,
                                MvtStkService mvtStkService, Conditionnements conditionnements,
                                SiteCourant siteCourant, Cloisonnement cloisonnement,
                                com.jumpy.tech.gestionstock.gestiondestock.service.ConditionnementService conditionnementService) {
        this.conditionnementService = conditionnementService;
        this.transfertRepository = transfertRepository;
        this.ligneRepository = ligneRepository;
        this.articleRepository = articleRepository;
        this.siteRepository = siteRepository;
        this.mvtStkService = mvtStkService;
        this.conditionnements = conditionnements;
        this.siteCourant = siteCourant;
        this.cloisonnement = cloisonnement;
    }

    @Override
    public List<TransfertDto> lister(EtatTransfert etat) {
        List<Transfert> transferts = transfertRepository.findAllByIdEntrepriseOrderByIdDesc(entreprise()).stream()
                .filter(t -> etat == null || t.getEtat() == etat)
                // L'equipe d'un site voit ce qui en part et ce qui y arrive, pas les echanges des autres.
                .filter(t -> siteCourant.peutVoir(t.getSource()) || siteCourant.peutVoir(t.getDestination()))
                .collect(Collectors.toList());
        Map<Long, List<LigneTransfertDto>> lignes = ligneRepository
                .findAllByTransfertIdIn(transferts.stream().map(Transfert::getId).toList()).stream()
                .collect(Collectors.groupingBy(l -> l.getTransfert().getId(),
                        Collectors.mapping(LigneTransfertDto::fromEntity, Collectors.toList())));
        return transferts.stream()
                .map(t -> TransfertDto.fromEntity(t, lignes.getOrDefault(t.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Override
    public TransfertDto detail(Long id) {
        return dto(transfert(id));
    }

    @Override
    @Transactional
    public TransfertDto creer(TransfertDto dto) {
        if (dto == null || dto.getIdSiteDestination() == null) {
            throw new InvalidEntityException("Un transfert va quelque part : choisissez le site d'arrivée",
                    ErrorCodes.TRANSFERT_NOT_VALID);
        }
        // Celui qui prepare le chargement travaille au depart.
        Site source = dto.getIdSiteSource() == null ? siteCourant.site() : siteCourant.accessible(dto.getIdSiteSource());
        if (source == null) {
            throw new InvalidEntityException("Aucun site de départ", ErrorCodes.TRANSFERT_NOT_VALID);
        }
        Site destination = siteRepository.findById(dto.getIdSiteDestination())
                .filter(s -> Objects.equals(s.getIdEntreprise(), source.getIdEntreprise()) && s.isActif())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun site avec l'identifiant " + dto.getIdSiteDestination() + " n'a été trouvé",
                        ErrorCodes.SITE_NOT_FOUND));
        if (destination.getId().equals(source.getId())) {
            throw new InvalidEntityException("Un transfert part d'un site pour un autre",
                    ErrorCodes.TRANSFERT_NOT_VALID, List.of("« " + source.getNom() + " » est au départ et à l'arrivée"));
        }

        Transfert transfert = new Transfert();
        transfert.setIdEntreprise(source.getIdEntreprise());
        transfert.setReference(referenceSuivante());
        transfert.setSource(source);
        transfert.setDestination(destination);
        transfert.setEtat(EtatTransfert.BROUILLON);
        transfert.setCommentaire(StringUtils.hasText(dto.getCommentaire()) ? dto.getCommentaire().trim() : null);
        Transfert enregistre = transfertRepository.save(transfert);
        if (dto.getLignes() != null) {
            dto.getLignes().forEach(ligne -> enregistrerLigne(enregistre, ligne));
        }
        return dto(enregistre);
    }

    @Override
    @Transactional
    public TransfertDto ajouterLigne(Long id, LigneTransfertDto ligne) {
        Transfert transfert = brouillon(id);
        enregistrerLigne(transfert, ligne);
        return dto(transfert);
    }

    @Override
    @Transactional
    public TransfertDto retirerLigne(Long id, Long idLigne) {
        Transfert transfert = brouillon(id);
        ligneRepository.delete(ligne(transfert, idLigne));
        return dto(transfert);
    }

    @Override
    @Transactional
    public TransfertDto expedier(Long id) {
        Transfert transfert = brouillon(id);
        List<LigneTransfert> lignes = ligneRepository.findAllByTransfertIdOrderByIdAsc(id);
        if (lignes.isEmpty()) {
            throw new InvalidEntityException("Un transfert sans ligne n'emporte rien", ErrorCodes.TRANSFERT_NOT_VALID);
        }
        // Le stock du depart s'oppose a la sortie : on ne charge pas ce que le depot n'a pas. Une
        // ligne en manque fait echouer tout le depart — un camion a moitie charge sans le dire
        // serait pire qu'un refus.
        for (LigneTransfert ligne : lignes) {
            mvtStkService.sortieStock(MvtStkDto.builder()
                    .article(ArticleDto.builder().Id(ligne.getArticle().getId()).build())
                    .quantite(Conditionnements.enUnitesDeBase(ligne.getQuantite(), ligne.getContenance()))
                    .motif(MotifMvtStk.TRANSFERT_SORTIE)
                    .idSite(transfert.getSource().getId())
                    .build());
        }
        transfert.setEtat(EtatTransfert.EXPEDIE);
        transfert.setDateExpedition(Instant.now());
        log.info("Transfert {} expedie de {} vers {}", transfert.getReference(),
                transfert.getSource().getNom(), transfert.getDestination().getNom());
        return dto(transfertRepository.save(transfert));
    }

    @Override
    @Transactional
    public TransfertDto recevoir(Long id, List<ReceptionTransfertDto> receptions) {
        Transfert transfert = transfert(id);
        if (transfert.getEtat() != EtatTransfert.EXPEDIE) {
            throw new InvalidEntityException(
                    "Seul un transfert en route se reçoit, celui-ci est " + transfert.getEtat(),
                    ErrorCodes.TRANSFERT_NOT_VALID);
        }
        // Celui qui decharge travaille a l'arrivee.
        siteCourant.accessible(transfert.getDestination().getId());

        List<LigneTransfert> lignes = ligneRepository.findAllByTransfertIdOrderByIdAsc(id);
        Map<Long, ReceptionTransfertDto> parLigne = new HashMap<>();
        if (receptions != null) {
            for (ReceptionTransfertDto reception : receptions) {
                if (lignes.stream().noneMatch(l -> l.getId().equals(reception.idLigne()))) {
                    throw new InvalidEntityException(
                            "La ligne " + reception.idLigne() + " n'appartient pas au transfert " + transfert.getReference(),
                            ErrorCodes.TRANSFERT_NOT_VALID);
                }
                parLigne.put(reception.idLigne(), reception);
            }
        }

        for (LigneTransfert ligne : lignes) {
            ReceptionTransfertDto reception = parLigne.get(ligne.getId());
            BigDecimal recue = reception == null || reception.quantiteRecue() == null
                    ? ligne.getQuantite() : reception.quantiteRecue();
            if (recue.signum() < 0) {
                throw new InvalidEntityException("Une quantité reçue n'est pas négative", ErrorCodes.TRANSFERT_NOT_VALID);
            }
            // Recevoir plus qu'il n'est parti n'est pas un transfert, c'est une erreur de comptage :
            // elle ne doit pas fabriquer du stock.
            if (recue.compareTo(ligne.getQuantite()) > 0) {
                throw new InvalidEntityException(
                        "Il est arrivé plus de « " + ligne.getArticle().getDesignation() + " » qu'il n'en est parti : "
                                + recue.stripTrailingZeros().toPlainString() + " pour "
                                + ligne.getQuantite().stripTrailingZeros().toPlainString(),
                        ErrorCodes.TRANSFERT_NOT_VALID, List.of("Recomptez le déchargement"));
            }
            Conditionnements.verifierFraction(ligne.getArticle(), ligne.getConditionnement(), recue,
                    ErrorCodes.TRANSFERT_NOT_VALID);
            String motif = reception == null || !StringUtils.hasText(reception.motifEcart())
                    ? null : reception.motifEcart().trim();
            if (recue.compareTo(ligne.getQuantite()) < 0 && motif == null) {
                throw new InvalidEntityException(
                        "Il manque « " + ligne.getArticle().getDesignation() + " » à l'arrivée : dites pourquoi",
                        ErrorCodes.TRANSFERT_NOT_VALID, List.of("Casse en route, oubli au chargement…"));
            }
            ligne.setQuantiteRecue(recue);
            ligne.setMotifEcart(recue.compareTo(ligne.getQuantite()) < 0 ? motif : null);
            ligneRepository.save(ligne);
            if (recue.signum() > 0) {
                mvtStkService.entreeStock(MvtStkDto.builder()
                        .article(ArticleDto.builder().Id(ligne.getArticle().getId()).build())
                        .quantite(Conditionnements.enUnitesDeBase(recue, ligne.getContenance()))
                        .motif(MotifMvtStk.TRANSFERT_ENTREE)
                        .idSite(transfert.getDestination().getId())
                        .build());
            }
        }
        transfert.setEtat(EtatTransfert.RECU);
        transfert.setDateReception(Instant.now());
        log.info("Transfert {} recu a {}", transfert.getReference(), transfert.getDestination().getNom());
        return dto(transfertRepository.save(transfert));
    }

    @Override
    @Transactional
    public TransfertDto annuler(Long id) {
        Transfert transfert = brouillon(id);
        transfert.setEtat(EtatTransfert.ANNULE);
        return dto(transfertRepository.save(transfert));
    }

    private void enregistrerLigne(Transfert transfert, LigneTransfertDto dto) {
        if (dto == null || dto.getArticle() == null || dto.getArticle().getId() == null) {
            throw new InvalidEntityException("Une ligne de transfert désigne un article", ErrorCodes.TRANSFERT_NOT_VALID);
        }
        Article article = articleRepository.findById(dto.getArticle().getId())
                .filter(a -> Objects.equals(a.getIdEntreprise(), transfert.getIdEntreprise()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + dto.getArticle().getId() + " n'a été trouvé",
                        ErrorCodes.ARTICLE_NOT_FOUND));
        BigDecimal quantite = dto.getQuantite();
        if (quantite == null || quantite.signum() <= 0) {
            throw new InvalidEntityException("La quantité transférée doit être strictement positive",
                    ErrorCodes.TRANSFERT_NOT_VALID);
        }
        // Un carton retire du catalogue ne se charge plus ; achetable ou vendable, peu importe :
        // on deplace la marchandise, on ne la vend pas.
        Conditionnement conditionnement = conditionnements.pourLigneConstatee(article, dto.getConditionnement());
        if (conditionnement != null && !conditionnement.isActif()) {
            throw new InvalidEntityException("Le conditionnement « " + conditionnement.getLibelle() + " » a été retiré",
                    ErrorCodes.TRANSFERT_NOT_VALID);
        }
        Conditionnements.verifierFraction(article, conditionnement, quantite, ErrorCodes.TRANSFERT_NOT_VALID);

        LigneTransfert ligne = new LigneTransfert();
        ligne.setTransfert(transfert);
        ligne.setArticle(article);
        ligne.setConditionnement(conditionnement);
        ligne.setContenance(Conditionnements.contenance(conditionnement));
        ligne.setQuantite(quantite);
        ligne.setIdEntreprise(transfert.getIdEntreprise());
        ligneRepository.save(ligne);
    }

    /** Un brouillon que l'appelant peut preparer : il travaille au site de depart. */
    private Transfert brouillon(Long id) {
        Transfert transfert = transfert(id);
        if (transfert.getEtat() != EtatTransfert.BROUILLON) {
            throw new InvalidEntityException(
                    "Le transfert " + transfert.getReference() + " est " + transfert.getEtat() + " : il ne se modifie plus",
                    ErrorCodes.TRANSFERT_NOT_VALID);
        }
        siteCourant.accessible(transfert.getSource().getId());
        return transfert;
    }

    private Transfert transfert(Long id) {
        Transfert transfert = transfertRepository.findById(id)
                .filter(t -> !cloisonnement.filtre() || Objects.equals(t.getIdEntreprise(), cloisonnement.entrepriseCourante()))
                .filter(t -> siteCourant.peutVoir(t.getSource()) || siteCourant.peutVoir(t.getDestination()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun transfert avec l'identifiant " + id + " n'a été trouvé", ErrorCodes.TRANSFERT_NOT_FOUND));
        return transfert;
    }

    private LigneTransfert ligne(Transfert transfert, Long idLigne) {
        return ligneRepository.findById(idLigne)
                .filter(l -> l.getTransfert().getId().equals(transfert.getId()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune ligne " + idLigne + " sur le transfert " + transfert.getReference(),
                        ErrorCodes.TRANSFERT_NOT_FOUND));
    }

    /** Le transfert et ses lignes, leurs articles avec leurs conditionnements : on charge au carton. */
    private TransfertDto dto(Transfert transfert) {
        List<LigneTransfertDto> lignes = ligneRepository.findAllByTransfertIdOrderByIdAsc(transfert.getId())
                .stream().map(LigneTransfertDto::fromEntity).collect(Collectors.toList());
        conditionnementService.completer(lignes.stream().map(LigneTransfertDto::getArticle).collect(Collectors.toList()));
        return TransfertDto.fromEntity(transfert, lignes);
    }

    private Long entreprise() {
        Long entreprise = cloisonnement.entrepriseCourante();
        if (entreprise == null) {
            throw new InvalidEntityException("Ce compte n'est rattaché à aucune entreprise", ErrorCodes.TRANSFERT_NOT_VALID);
        }
        return entreprise;
    }

    /** `TR-2026-000012`, comme les inventaires : l'annee se lit, le rang ne se rejoue pas. */
    private String referenceSuivante() {
        long rang = transfertRepository.prochaineReference();
        int annee = Instant.now().atZone(ZoneId.systemDefault()).getYear();
        return String.format("TR-%d-%06d", annee, rang);
    }
}
