package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.Impl.MvtStkServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les regles du mouvement de stock, sans base : elles ne dependent que de l'arithmetique des
 * entrees et des sorties.
 */
class MvtStkServiceTest {

    private static final Long ID_ARTICLE = 7L;

    private MvtStkRepository mvtStkRepository;
    private ArticleRepository articleRepository;
    private MvtStkService service;

    @BeforeEach
    void setUp() {
        mvtStkRepository = mock(MvtStkRepository.class);
        articleRepository = mock(ArticleRepository.class);
        service = new MvtStkServiceImpl(mvtStkRepository, articleRepository);

        Article article = new Article();
        article.setId(ID_ARTICLE);
        article.setCodeArticle("ART-001");
        when(articleRepository.findById(ID_ARTICLE)).thenReturn(Optional.of(article));
        when(mvtStkRepository.save(any(MvtStk.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stockEnMagasin(String entrees, String sorties) {
        when(mvtStkRepository.sommeParType(ID_ARTICLE, TypeMvtStk.ENTREE)).thenReturn(new BigDecimal(entrees));
        when(mvtStkRepository.sommeParType(ID_ARTICLE, TypeMvtStk.SORTIE)).thenReturn(new BigDecimal(sorties));
    }

    private MvtStkDto mouvementDe(String quantite) {
        return MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ID_ARTICLE).build())
                .quantite(new BigDecimal(quantite))
                .build();
    }

    @Test
    void le_stock_reel_est_la_difference_entre_entrees_et_sorties() {
        stockEnMagasin("100", "30");

        assertThat(service.stockReelArticle(ID_ARTICLE)).isEqualByComparingTo("70");
    }

    @Test
    void un_article_sans_mouvement_est_a_zero() {
        stockEnMagasin("0", "0");

        assertThat(service.stockReelArticle(ID_ARTICLE)).isEqualByComparingTo("0");
    }

    @Test
    void une_entree_est_enregistree_dans_le_bon_sens() {
        stockEnMagasin("0", "0");

        MvtStkDto enregistre = service.entreeStock(mouvementDe("12"));

        assertThat(enregistre.getTypeMvt()).isEqualTo(TypeMvtStk.ENTREE);
        assertThat(enregistre.getQuantite()).isEqualByComparingTo("12");
        assertThat(enregistre.getDateMvt()).isNotNull();
    }

    @Test
    void une_sortie_couverte_par_le_stock_passe() {
        stockEnMagasin("50", "20");

        MvtStkDto enregistre = service.sortieStock(mouvementDe("30"));

        assertThat(enregistre.getTypeMvt()).isEqualTo(TypeMvtStk.SORTIE);
        assertThat(enregistre.getQuantite()).isEqualByComparingTo("30");
    }

    @Test
    void une_sortie_qui_depasse_le_stock_est_refusee_et_rien_n_est_ecrit() {
        stockEnMagasin("50", "45");

        assertThatThrownBy(() -> service.sortieStock(mouvementDe("10")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Stock insuffisant")
                .extracting(e -> ((InvalidEntityException) e).getErrorCode())
                .isEqualTo(ErrorCodes.STOCK_INSUFFISANT);

        verify(mvtStkRepository, never()).save(any());
    }

    @Test
    void le_sens_vient_de_la_methode_appelee_et_non_du_dto() {
        stockEnMagasin("0", "0");

        // Un client malveillant poste une « entree » sur la route de sortie : c'est la route qui
        // decide, sans quoi il suffirait de mentir sur le type pour creer du stock.
        MvtStkDto mensonge = MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ID_ARTICLE).build())
                .quantite(new BigDecimal("5"))
                .typeMvt(TypeMvtStk.ENTREE)
                .build();

        assertThatThrownBy(() -> service.sortieStock(mensonge))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Stock insuffisant");
    }

    @Test
    void une_quantite_negative_ou_nulle_est_refusee() {
        stockEnMagasin("100", "0");

        assertThatThrownBy(() -> service.entreeStock(mouvementDe("-5")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("strictement positive");

        assertThatThrownBy(() -> service.entreeStock(mouvementDe("0")))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("strictement positive");

        verify(mvtStkRepository, never()).save(any());
    }

    @Test
    void un_mouvement_sur_un_article_inconnu_est_un_404_metier() {
        when(articleRepository.findById(404L)).thenReturn(Optional.empty());

        MvtStkDto dto = MvtStkDto.builder()
                .article(ArticleDto.builder().Id(404L).build())
                .quantite(BigDecimal.ONE)
                .build();

        assertThatThrownBy(() -> service.entreeStock(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting(e -> ((EntityNotFoundException) e).getErrorCode())
                .isEqualTo(ErrorCodes.ARTICLE_NOT_FOUND);
    }

    @Test
    void un_mouvement_sans_article_est_refuse() {
        assertThatThrownBy(() -> service.entreeStock(MvtStkDto.builder().quantite(BigDecimal.ONE).build()))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("designe un article");
    }
}
