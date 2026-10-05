package com.jumpy.tech.gestionstock.gestiondestock.lot;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LotDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RappelLotDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.StockSiteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Lot;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LotRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.SiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class LotServiceImpl implements LotService {

    private static final Comparator<LotDto> PREMIER_PERIME = Comparator
            .comparing(LotDto::getDatePeremption, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LotDto::getId);

    private final LotRepository lotRepository;
    private final MvtStkRepository mvtStkRepository;
    private final ArticleRepository articleRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final SiteRepository siteRepository;
    private final VenteRepository venteRepository;
    private final SiteCourant siteCourant;
    private final Cloisonnement cloisonnement;
    private final Calendrier calendrier;

    public LotServiceImpl(LotRepository lotRepository, MvtStkRepository mvtStkRepository,
                          ArticleRepository articleRepository, EntrepriseRepository entrepriseRepository,
                          SiteRepository siteRepository, VenteRepository venteRepository,
                          SiteCourant siteCourant, Cloisonnement cloisonnement, Calendrier calendrier) {
        this.lotRepository = lotRepository;
        this.mvtStkRepository = mvtStkRepository;
        this.articleRepository = articleRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.siteRepository = siteRepository;
        this.venteRepository = venteRepository;
        this.siteCourant = siteCourant;
        this.cloisonnement = cloisonnement;
        this.calendrier = calendrier;
    }

    @Override
    public List<LotDto> lotsDeLArticle(Long idArticle) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + idArticle + " n'a été trouvé", ErrorCodes.ARTICLE_NOT_FOUND));
        cloisonnement.verifierAcces(article.getIdEntreprise(), "article", idArticle);

        List<Lot> lotsArticle = lotRepository.findAllByArticleIdOrderByDatePeremptionAscIdAsc(article.getId());
        Map<Long, Map<Long, BigDecimal>> stocks = stocks(lotsArticle.stream().map(Lot::getId).toList());
        Map<Long, Site> sites = sitesVisibles(article.getIdEntreprise());
        Site actif = siteCourant.site();
        int delaiMagasin = delaiMagasin(article.getIdEntreprise());

        List<LotDto> resultat = new ArrayList<>();
        for (Lot lot : lotsArticle) {
            Map<Long, BigDecimal> parSite = stocks.getOrDefault(lot.getId(), Map.of());
            List<StockSiteDto> ou = parSite.entrySet().stream()
                    .filter(e -> e.getValue().signum() > 0 && sites.containsKey(e.getKey()))
                    .map(e -> stockSite(sites.get(e.getKey()), e.getValue()))
                    .toList();
            if (ou.isEmpty()) {
                continue;
            }
            LotDto dto = decrire(lot, delaiMagasin);
            dto.setParSite(ou);
            if (actif != null) {
                dto.setIdSite(actif.getId());
                dto.setNomSite(actif.getNom());
                dto.setQuantite(parSite.getOrDefault(actif.getId(), BigDecimal.ZERO).max(BigDecimal.ZERO));
            } else {
                dto.setQuantite(ou.stream().map(StockSiteDto::quantite).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            resultat.add(dto);
        }
        resultat.sort(PREMIER_PERIME);
        return resultat;
    }

    @Override
    public List<LotDto> peremption(boolean tousSites) {
        Long entreprise = cloisonnement.entrepriseCourante();
        if (entreprise == null) {
            return List.of();
        }
        Map<Long, Site> sites = sitesVisibles(entreprise);
        if (!tousSites) {
            Site actif = siteCourant.site();
            sites.keySet().retainAll(actif == null ? List.of() : List.of(actif.getId()));
        }
        List<Lot> dates = lotRepository.findAllByIdEntrepriseAndDatePeremptionNotNull(entreprise);
        Map<Long, Map<Long, BigDecimal>> stocks = stocks(dates.stream().map(Lot::getId).toList());
        int delaiMagasin = delaiMagasin(entreprise);

        List<LotDto> resultat = new ArrayList<>();
        for (Lot lot : dates) {
            if ("BON".equals(decrire(lot, delaiMagasin).getEtat())) {
                continue;
            }
            stocks.getOrDefault(lot.getId(), Map.of()).forEach((idSite, quantite) -> {
                if (quantite.signum() > 0 && sites.containsKey(idSite)) {
                    LotDto ligne = decrire(lot, delaiMagasin);
                    ligne.setIdSite(idSite);
                    ligne.setNomSite(sites.get(idSite).getNom());
                    ligne.setQuantite(quantite);
                    resultat.add(ligne);
                }
            });
        }
        resultat.sort(PREMIER_PERIME.thenComparing(LotDto::getNomSite));
        return resultat;
    }

    @Override
    public RappelLotDto rappel(Long idLot) {
        Lot lot = lotRepository.findById(idLot)
                .filter(l -> !cloisonnement.filtre()
                        || Objects.equals(l.getIdEntreprise(), cloisonnement.entrepriseCourante()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun lot avec l'identifiant " + idLot + " n'a été trouvé", ErrorCodes.LOT_NOT_FOUND));
        Map<Long, Site> sites = sitesVisibles(lot.getIdEntreprise());

        List<StockSiteDto> stocks = stocks(List.of(lot.getId())).getOrDefault(lot.getId(), Map.of()).entrySet().stream()
                .filter(e -> e.getValue().signum() > 0 && sites.containsKey(e.getKey()))
                .map(e -> stockSite(sites.get(e.getKey()), e.getValue()))
                .toList();

        Map<Long, BigDecimal> parVente = new HashMap<>();
        for (Object[] ligne : mvtStkRepository.ventesDuLot(lot.getId(), TypeMvtStk.SORTIE)) {
            if (((BigDecimal) ligne[1]).signum() > 0) {
                parVente.put((Long) ligne[0], (BigDecimal) ligne[1]);
            }
        }
        // Une vente annulee a rendu sa marchandise : son net est nul, elle n'apparait donc pas.
        List<RappelLotDto.VenteDuLot> ventes = venteRepository.findAllById(parVente.keySet()).stream()
                .sorted(Comparator.comparing(Vente::getDatevente, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(v -> {
                    Client client = v.getClient();
                    Site site = v.getSiteExpedition() != null ? v.getSiteExpedition() : v.getSite();
                    return new RappelLotDto.VenteDuLot(v.getId(), v.getCode(), v.getCodeTicket(), v.getDatevente(),
                            site == null ? null : site.getNom(),
                            client == null ? null : nomComplet(client),
                            client == null ? null : client.getNumTel(),
                            parVente.get(v.getId()));
                })
                .toList();

        LotDto dto = decrire(lot, delaiMagasin(lot.getIdEntreprise()));
        dto.setParSite(stocks);
        dto.setQuantite(stocks.stream().map(StockSiteDto::quantite).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new RappelLotDto(dto, stocks, ventes);
    }

    /** Le lot, et ou en est sa date : perime, bientot (dans le delai d'alerte), ou bon. */
    private LotDto decrire(Lot lot, int delaiMagasin) {
        LotDto dto = LotDto.de(lot);
        if (lot.getDatePeremption() == null) {
            dto.setEtat("BON");
            return dto;
        }
        long jours = ChronoUnit.DAYS.between(calendrier.aujourdhui(), lot.getDatePeremption());
        Integer delaiArticle = lot.getArticle().getDelaiAlertePeremption();
        int delai = delaiArticle != null ? delaiArticle : delaiMagasin;
        dto.setJoursRestants(jours);
        dto.setEtat(jours < 0 ? "PERIME" : jours <= delai ? "BIENTOT" : "BON");
        return dto;
    }

    /** Lot, puis site, puis quantite. */
    private Map<Long, Map<Long, BigDecimal>> stocks(Collection<Long> idsLots) {
        Map<Long, Map<Long, BigDecimal>> stocks = new HashMap<>();
        if (idsLots.isEmpty()) {
            return stocks;
        }
        for (Object[] ligne : mvtStkRepository.stocksDesLots(idsLots, TypeMvtStk.ENTREE)) {
            stocks.computeIfAbsent((Long) ligne[0], k -> new HashMap<>()).put((Long) ligne[1], (BigDecimal) ligne[2]);
        }
        return stocks;
    }

    private Map<Long, Site> sitesVisibles(Long idEntreprise) {
        if (idEntreprise == null) {
            return new HashMap<>();
        }
        return siteRepository.findAllByIdEntrepriseOrderByPrincipalDescNomAsc(idEntreprise).stream()
                .filter(siteCourant::peutVoir)
                .collect(Collectors.toMap(Site::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private int delaiMagasin(Long idEntreprise) {
        return idEntreprise == null ? 30 : entrepriseRepository.findById(idEntreprise)
                .map(Entreprise::getDelaiAlertePeremption).orElse(30);
    }

    private static StockSiteDto stockSite(Site site, BigDecimal quantite) {
        return new StockSiteDto(site.getId(), site.getNom(), site.getType(), quantite);
    }

    private static String nomComplet(Client client) {
        String prenoms = client.getPrenoms() == null ? "" : client.getPrenoms().trim();
        String nom = client.getNom() == null ? "" : client.getNom().trim();
        return (prenoms + " " + nom).trim();
    }
}
