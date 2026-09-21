package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.ArticleService;
import com.jumpy.tech.gestionstock.gestiondestock.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les routes de consultation du catalogue.
 *
 * La recherche par code partageait le motif de la recherche par identifiant : `/articles/{id}` et
 * `/articles/{code}` sont le meme chemin pour Spring, et la seconde etait donc inatteignable. Ce
 * test la joint pour de bon.
 */
@AutoConfigureMockMvc
class CatalogueApiTest extends AbstractIntegrationTest {

    private static final String API = "/gestiondestock/v1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;

    private String codeArticle;

    @BeforeEach
    void creerUnArticle() {
        CategoryDto category = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID())
                .designation("Papeterie")
                .build());

        codeArticle = "ART-" + UUID.randomUUID();
        articleService.save(ArticleDto.builder()
                .codeArticle(codeArticle)
                .designation("Cahier 200 pages")
                .prixUnitaireHt(new BigDecimal("1200"))
                .tauxTva(new BigDecimal("19.25"))
                .category(category)
                .build());
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void la_recherche_par_code_repond() throws Exception {
        mockMvc.perform(get(API + "/articles/code/" + codeArticle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeArticle").value(codeArticle));
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void un_code_inconnu_donne_un_404_et_non_un_500() throws Exception {
        // Le motif `Optional.get()` avant `orElseThrow` rendait ici une 500.
        mockMvc.perform(get(API + "/articles/code/ART-INEXISTANT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ARTICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void la_liste_paginee_rend_une_tranche_et_le_total() throws Exception {
        mockMvc.perform(get(API + "/articles?page=0&size=1&sort=codeArticle,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @WithMockUser(roles = "CAISSIER")
    void la_liste_complete_reste_disponible() throws Exception {
        mockMvc.perform(get(API + "/articles/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
