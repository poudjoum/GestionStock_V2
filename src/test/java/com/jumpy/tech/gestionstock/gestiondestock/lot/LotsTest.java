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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Premier perime, premier sorti : de quel lot part ce qu'on vend. */
class LotsTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 5);

    private LotRepository lotRepository;
    private MvtStkRepository mvtStkRepository;
    private Lots lots;
    private Article article;
    private Site site;

    @BeforeEach
    void setUp() {
        lotRepository = mock(LotRepository.class);
        mvtStkRepository = mock(MvtStkRepository.class);
        ZoneId douala = ZoneId.of("Africa/Douala");
        lots = new Lots(lotRepository, mvtStkRepository,
                Calendrier.fixe(AUJOURDHUI.atStartOfDay(douala).plusHours(10).toInstant(), douala));

        article = new Article();
        article.setId(1L);
        article.setCodeArticle("YAOURT");
        article.setDesignation("Yaourt nature");
        article.setSuiviLot(true);
        article.setTypeDate(TypeDate.DLC);
        site = new Site();
        site.setId(10L);
        site.setNom("Magasin");
    }

    @Test
    void le_lot_qui_perime_le_premier_sort_le_premier() {
        Lot tardif = lot(1L, "L-TARD", AUJOURDHUI.plusDays(60));
        Lot proche = lot(2L, "L-PROCHE", AUJOURDHUI.plusDays(5));
        stocks(null, Map.of(1L, "10", 2L, "4"), tardif, proche);

        List<Lots.Part> parts = lots.allouer(article, site, new BigDecimal("6"), true, new ArrayList<>());

        assertThat(parts).extracting(p -> p.lot().getNumero(), p -> p.quantite().intValue())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("L-PROCHE", 4),
                        org.assertj.core.groups.Tuple.tuple("L-TARD", 2));
    }

    @Test
    void le_stock_sans_lot_sort_avant_les_lots() {
        Lot lot = lot(1L, "L1", AUJOURDHUI.plusDays(5));
        stocks("3", Map.of(1L, "10"), lot);

        List<Lots.Part> parts = lots.allouer(article, site, new BigDecimal("5"), true, new ArrayList<>());

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).lot()).isNull();
        assertThat(parts.get(0).quantite()).isEqualByComparingTo("3");
        assertThat(parts.get(1).lot()).isSameAs(lot);
        assertThat(parts.get(1).quantite()).isEqualByComparingTo("2");
    }

    @Test
    void un_lot_dlc_depasse_ne_se_vend_pas_au_comptoir() {
        Lot perime = lot(1L, "L-VIEUX", AUJOURDHUI.minusDays(1));
        Lot bon = lot(2L, "L-BON", AUJOURDHUI.plusDays(20));
        stocks(null, Map.of(1L, "5", 2L, "2"), perime, bon);

        assertThatThrownBy(() -> lots.allouer(article, site, new BigDecimal("4"), true, new ArrayList<>()))
                .isInstanceOf(InvalidEntityException.class)
                .satisfies(e -> {
                    InvalidEntityException erreur = (InvalidEntityException) e;
                    assertThat(erreur.getErrorCode()).isEqualTo(ErrorCodes.STOCK_INSUFFISANT);
                    assertThat(erreur.getErrors()).anyMatch(m -> m.contains("date limite dépassée"));
                });
    }

    @Test
    void un_lot_dluo_depasse_se_vend_avec_un_avertissement() {
        article.setTypeDate(TypeDate.DLUO);
        Lot depasse = lot(1L, "L-DLUO", AUJOURDHUI.minusDays(3));
        stocks(null, Map.of(1L, "5"), depasse);
        List<String> avertissements = new ArrayList<>();

        List<Lots.Part> parts = lots.allouer(article, site, new BigDecimal("2"), true, avertissements);

        assertThat(parts).singleElement().satisfies(p -> assertThat(p.lot()).isSameAs(depasse));
        assertThat(avertissements).singleElement().asString().contains("L-DLUO");
    }

    @Test
    void une_vente_faite_hors_ligne_sort_meme_sans_stock() {
        Lot perime = lot(1L, "L-VIEUX", AUJOURDHUI.minusDays(1));
        stocks(null, Map.of(1L, "1"), perime);

        List<Lots.Part> parts = lots.allouer(article, site, new BigDecimal("3"), false, null);

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).lot()).isSameAs(perime);
        assertThat(parts.get(1).lot()).isNull();
        assertThat(parts.get(1).quantite()).isEqualByComparingTo("2");
    }

    @Test
    void un_numero_connu_avec_une_autre_date_est_refuse() {
        Lot connu = lot(1L, "L1", AUJOURDHUI.plusDays(30));
        when(lotRepository.findByArticleIdAndNumeroIgnoreCase(anyLong(), anyString())).thenReturn(Optional.of(connu));

        assertThatThrownBy(() -> lots.pourEntree(article, "l1", AUJOURDHUI.plusDays(31)))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("déjà connu");
    }

    @Test
    void un_article_suivi_exige_son_numero_de_lot() {
        assertThatThrownBy(() -> lots.pourEntree(article, " ", null))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("numéro de lot");
    }

    private Lot lot(Long id, String numero, LocalDate date) {
        Lot lot = new Lot();
        lot.setId(id);
        lot.setArticle(article);
        lot.setNumero(numero);
        lot.setDatePeremption(date);
        return lot;
    }

    @SuppressWarnings("unchecked")
    private void stocks(String sansLot, Map<Long, String> parLot, Lot... connus) {
        List<Object[]> lignes = new ArrayList<>();
        if (sansLot != null) {
            lignes.add(new Object[]{null, new BigDecimal(sansLot)});
        }
        parLot.forEach((id, q) -> lignes.add(new Object[]{id, new BigDecimal(q)}));
        when(mvtStkRepository.stocksParLotDansSite(1L, 10L, TypeMvtStk.ENTREE)).thenReturn(lignes);
        when(lotRepository.findAllById(any(Iterable.class))).thenReturn(List.of(connus));
    }
}
