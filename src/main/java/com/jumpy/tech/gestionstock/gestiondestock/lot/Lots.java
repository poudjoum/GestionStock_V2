package com.jumpy.tech.gestionstock.gestiondestock.lot;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Lot;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeDate;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LotRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Les lots d'un article : de quel lot sort ce qu'on vend, et quel lot entre quand on recoit.
 *
 * La regle de sortie est « premier perime, premier sorti » (FEFO). Le stock sans lot — celui
 * d'avant que l'article soit suivi — sort avant tout : il est le plus ancien, et personne ne sait
 * plus quand il perime.
 */
@Component
public class Lots {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Une part d'une sortie : ce lot-ci (nul pour le stock sans lot), cette quantite. */
    public record Part(Lot lot, BigDecimal quantite) {
    }

    private final LotRepository lotRepository;
    private final MvtStkRepository mvtStkRepository;
    private final Calendrier calendrier;

    public Lots(LotRepository lotRepository, MvtStkRepository mvtStkRepository, Calendrier calendrier) {
        this.lotRepository = lotRepository;
        this.mvtStkRepository = mvtStkRepository;
        this.calendrier = calendrier;
    }

    /**
     * Repartit une sortie sur les lots du site.
     *
     * `strict` — une vente au comptoir, un transfert : un lot DLC depasse ne sort pas, et si ce qui
     * reste ne suffit pas, la sortie est refusee en disant pourquoi. Un lot DLUO depasse sort, et
     * l'avertissement le dit. Hors `strict` — une vente faite hors ligne, un rattrapage
     * d'inventaire : la marchandise est deja partie ; tout est pris dans l'ordre, et ce qui manque
     * encore est impute au stock sans lot, qui passe sous zero.
     */
    public List<Part> allouer(Article article, Site site, BigDecimal quantite, boolean strict, List<String> avertissements) {
        LocalDate aujourdhui = calendrier.aujourdhui();
        Map<Long, BigDecimal> parLot = new HashMap<>();
        BigDecimal sansLot = BigDecimal.ZERO;
        for (Object[] ligne : mvtStkRepository.stocksParLotDansSite(article.getId(), site.getId(), TypeMvtStk.ENTREE)) {
            if (ligne[0] == null) {
                sansLot = (BigDecimal) ligne[1];
            } else {
                parLot.put((Long) ligne[0], (BigDecimal) ligne[1]);
            }
        }
        List<Lot> lots = lotRepository.findAllById(parLot.keySet()).stream()
                .filter(l -> parLot.get(l.getId()).signum() > 0)
                .sorted(Comparator.comparing(Lot::getDatePeremption, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Lot::getId))
                .toList();

        List<Part> parts = new ArrayList<>();
        BigDecimal reste = quantite;
        if (sansLot.signum() > 0) {
            BigDecimal pris = reste.min(sansLot);
            parts.add(new Part(null, pris));
            reste = reste.subtract(pris);
        }
        BigDecimal perimesEcartes = BigDecimal.ZERO;
        for (Lot lot : lots) {
            if (reste.signum() <= 0) {
                break;
            }
            BigDecimal disponible = parLot.get(lot.getId());
            boolean perime = lot.perimeLe(aujourdhui);
            if (perime && strict && article.getTypeDate() == TypeDate.DLC) {
                perimesEcartes = perimesEcartes.add(disponible);
                continue;
            }
            BigDecimal pris = reste.min(disponible);
            parts.add(new Part(lot, pris));
            reste = reste.subtract(pris);
            if (perime && avertissements != null) {
                avertissements.add("« " + article.getDesignation() + " » : le lot " + lot.getNumero()
                        + " a dépassé sa date (" + JOUR.format(lot.getDatePeremption()) + ")");
            }
        }

        if (reste.signum() > 0) {
            if (strict) {
                BigDecimal disponible = quantite.subtract(reste);
                throw new InvalidEntityException(
                        "Stock insuffisant pour l'article " + article.getCodeArticle() + " à « " + site.getNom() + " » : "
                                + disponible.stripTrailingZeros().toPlainString() + " vendables, "
                                + quantite.stripTrailingZeros().toPlainString() + " demandés",
                        ErrorCodes.STOCK_INSUFFISANT,
                        perimesEcartes.signum() > 0
                                ? List.of(perimesEcartes.stripTrailingZeros().toPlainString()
                                        + " sont dans des lots à date limite dépassée : ils ne se vendent plus")
                                : List.of());
            }
            parts.add(new Part(null, reste));
        }
        return fusionner(parts);
    }

    /**
     * Le stock d'un lot dans un site, pour une sortie qui designe son lot — un lot scanne, une
     * peremption declaree.
     */
    public BigDecimal stockDuLot(Article article, Site site, Lot lot) {
        return mvtStkRepository.stocksParLotDansSite(article.getId(), site.getId(), TypeMvtStk.ENTREE).stream()
                .filter(l -> Objects.equals(l[0], lot.getId()))
                .map(l -> (BigDecimal) l[1])
                .findFirst().orElse(BigDecimal.ZERO);
    }

    /**
     * Le lot d'une entree : retrouve par son numero, cree s'il est nouveau.
     *
     * Un numero deja connu avec une autre date est refuse : c'est une erreur de saisie, et la
     * laisser passer ferait perimer le meme lot deux fois.
     */
    public Lot pourEntree(Article article, String numero, LocalDate date) {
        if (!StringUtils.hasText(numero)) {
            throw new InvalidEntityException(
                    "« " + article.getDesignation() + " » est suivi par lot : indiquez le numéro de lot",
                    ErrorCodes.MVT_STK_NOT_VALID, List.of("Il est imprimé sur l'emballage, souvent après « LOT » ou « L »"));
        }
        String propre = numero.trim();
        if (propre.length() > 60) {
            throw new InvalidEntityException("Un numéro de lot ne dépasse pas 60 caractères", ErrorCodes.MVT_STK_NOT_VALID);
        }
        return lotRepository.findByArticleIdAndNumeroIgnoreCase(article.getId(), propre)
                .map(connu -> {
                    if (date != null && connu.getDatePeremption() != null && !date.equals(connu.getDatePeremption())) {
                        throw new InvalidEntityException(
                                "Le lot " + connu.getNumero() + " est déjà connu avec la date du "
                                        + JOUR.format(connu.getDatePeremption()),
                                ErrorCodes.MVT_STK_NOT_VALID, List.of("Vérifiez la date ou le numéro sur l'emballage"));
                    }
                    if (connu.getDatePeremption() == null && date != null) {
                        connu.setDatePeremption(date);
                        return lotRepository.save(connu);
                    }
                    return connu;
                })
                .orElseGet(() -> {
                    if (article.getTypeDate() != null && date == null) {
                        throw new InvalidEntityException(
                                "« " + article.getDesignation() + " » porte une " + article.getTypeDate()
                                        + " : indiquez la date du lot " + propre,
                                ErrorCodes.MVT_STK_NOT_VALID);
                    }
                    Lot lot = new Lot();
                    lot.setArticle(article);
                    lot.setIdEntreprise(article.getIdEntreprise());
                    lot.setNumero(propre);
                    lot.setDatePeremption(date);
                    return lotRepository.save(lot);
                });
    }

    /** Deux parts du meme lot n'en font qu'une : un mouvement par lot, pas par passage de boucle. */
    private static List<Part> fusionner(List<Part> parts) {
        Map<Long, Part> parLot = new java.util.LinkedHashMap<>();
        for (Part part : parts) {
            Long cle = part.lot() == null ? -1L : part.lot().getId();
            parLot.merge(cle, part, (a, b) -> new Part(a.lot(), a.quantite().add(b.quantite())));
        }
        return new ArrayList<>(parLot.values());
    }
}
