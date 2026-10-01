package com.jumpy.tech.gestionstock.gestiondestock.promotion;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CampagnePubliqueDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Campagne;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.PromotionArticle;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CampagneRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La vitrine de l'application mobile : les campagnes en cours, tous magasins confondus.
 *
 * Elle se lit sans compte — le client la parcourt avant de s'inscrire — et ne montre que ce
 * qu'un magasin affiche deja en rayon : ses prix et ses promotions. Un magasin dont l'abonnement
 * est echu ou suspendu n'y figure plus.
 */
@Service
public class CampagnesPubliques {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final CampagneRepository campagnes;
    private final EntrepriseRepository entreprises;
    private final Calendrier calendrier;

    public CampagnesPubliques(CampagneRepository campagnes, EntrepriseRepository entreprises, Calendrier calendrier) {
        this.campagnes = campagnes;
        this.entreprises = entreprises;
        this.calendrier = calendrier;
    }

    @Transactional(readOnly = true)
    public List<CampagnePubliqueDto> enCours() {
        LocalDate aujourdhui = calendrier.aujourdhui();
        List<Campagne> enCours = campagnes.toutesEnCours(aujourdhui);
        Map<Long, Entreprise> magasins = entreprises.findAllById(
                        enCours.stream().map(Campagne::getIdEntreprise).distinct().toList()).stream()
                .collect(Collectors.toMap(Entreprise::getId, Function.identity()));

        List<CampagnePubliqueDto> vitrine = new ArrayList<>();
        for (Campagne campagne : enCours) {
            Entreprise magasin = magasins.get(campagne.getIdEntreprise());
            if (magasin == null || !magasin.accesOuvert(aujourdhui)) {
                continue;
            }
            vitrine.add(CampagnePubliqueDto.builder()
                    .id(campagne.getId())
                    .titre(campagne.getTitre())
                    .message(campagne.getMessage())
                    .image(campagne.getImage())
                    .dateDebut(campagne.getDateDebut())
                    .dateFin(campagne.getDateFin())
                    .idMagasin(magasin.getId())
                    .nomMagasin(magasin.getNom())
                    .villeMagasin(magasin.getAdresse() == null ? null : magasin.getAdresse().getVille())
                    .logoMagasin(magasin.getLogo())
                    .articles(campagne.getPromotions().stream().map(p -> article(p, magasin)).toList())
                    .build());
        }
        return vitrine;
    }

    private static CampagnePubliqueDto.Article article(PromotionArticle promotion, Entreprise magasin) {
        Article article = promotion.getArticle();
        BigDecimal normalHt = article.getPrixUnitaire();
        BigDecimal promoHt = PrixPromotionnel.prix(normalHt, promotion.getTypeRemise(), promotion.getValeur());
        int remise = normalHt == null || normalHt.signum() == 0 ? 0
                : CENT.subtract(promoHt.multiply(CENT).divide(normalHt, 0, RoundingMode.HALF_UP)).intValue();
        return CampagnePubliqueDto.Article.builder()
                .id(article.getId())
                .designation(article.getDesignation())
                .photo(article.getPhoto())
                .prixNormalTtc(ttc(normalHt, article, magasin))
                .prixPromoTtc(ttc(promoHt, article, magasin))
                .remisePourcent(remise)
                .build();
    }

    /** Le prix que le client paiera : la TVA du magasin s'il la collecte, au taux de l'article d'abord. */
    static BigDecimal ttc(BigDecimal ht, Article article, Entreprise magasin) {
        if (ht == null) {
            return null;
        }
        BigDecimal taux = !magasin.isAssujettieTva() ? BigDecimal.ZERO
                : article.getTauxTva() != null ? article.getTauxTva()
                : magasin.getTauxTva() != null ? magasin.getTauxTva() : BigDecimal.ZERO;
        return ht.multiply(CENT.add(taux)).divide(CENT, 0, RoundingMode.HALF_UP);
    }
}
