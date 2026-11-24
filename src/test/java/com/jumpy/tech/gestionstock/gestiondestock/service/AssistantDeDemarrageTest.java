package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.demarrage.Demarrage;
import com.jumpy.tech.gestionstock.gestiondestock.demarrage.DemarrageDto;
import com.jumpy.tech.gestionstock.gestiondestock.demarrage.EtapeDemarrage;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Adresse;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Category;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CategoryRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'assistant de demarrage lit l'avancement dans les donnees du commerce, et rien d'autre.
 */
class AssistantDeDemarrageTest extends AbstractIntegrationTest {

    @Autowired
    private Demarrage demarrage;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private Long idEntreprise;

    @BeforeEach
    void unCommerceQuiArrive() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Épicerie " + UUID.randomUUID())
                .tel("690000000")
                .build()).getId();
        UserDetailsImpl gerant = new UserDetailsImpl(1L, "gerant-" + UUID.randomUUID(), "gerant@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(gerant, null, gerant.getAuthorities()));
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private static DemarrageDto.EtapeDto etape(DemarrageDto etat, EtapeDemarrage code) {
        return etat.getEtapes().stream().filter(e -> e.getCode() == code).findFirst().orElseThrow();
    }

    @Test
    @Transactional
    void un_commerce_qui_vient_d_arriver_a_tout_a_faire() {
        DemarrageDto etat = demarrage.etat();

        assertThat(etat.getEtapes()).extracting(DemarrageDto.EtapeDto::getCode)
                .containsExactly(EtapeDemarrage.values());
        assertThat(etat.getEtapes()).noneMatch(DemarrageDto.EtapeDto::isFaite);
        assertThat(etat.isTermine()).isFalse();
        assertThat(etat.isMasque()).isFalse();
        assertThat(etape(etat, EtapeDemarrage.EQUIPE).isFacultative()).isTrue();
        assertThat(etape(etat, EtapeDemarrage.CATEGORIES).getNombre()).isZero();
    }

    @Test
    @Transactional
    void les_etapes_avancent_avec_les_donnees_du_commerce() {
        Entreprise entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow();
        Adresse adresse = new Adresse();
        adresse.setVille("Douala");
        entreprise.setAdresse(adresse);
        entrepriseRepository.save(entreprise);

        Category boissons = new Category();
        boissons.setCodeCat("BOI-" + UUID.randomUUID());
        boissons.setDesignation("Boissons");
        boissons.setIdEntreprise(idEntreprise);
        categoryRepository.save(boissons);

        DemarrageDto etat = demarrage.etat();

        assertThat(etape(etat, EtapeDemarrage.IDENTITE).isFaite()).isTrue();
        assertThat(etape(etat, EtapeDemarrage.CATEGORIES).isFaite()).isTrue();
        assertThat(etape(etat, EtapeDemarrage.CATEGORIES).getNombre()).isEqualTo(1L);
        assertThat(etape(etat, EtapeDemarrage.ARTICLES).isFaite()).isFalse();
        assertThat(etat.isTermine()).isFalse();
    }

    @Test
    @Transactional
    void la_categorie_d_un_autre_commerce_ne_compte_pas() {
        Long voisin = entrepriseService.save(EntrepriseDto.builder()
                .nom("Voisin " + UUID.randomUUID()).tel("690000001").build()).getId();
        Category chezLeVoisin = new Category();
        chezLeVoisin.setCodeCat("VOI-" + UUID.randomUUID());
        chezLeVoisin.setDesignation("Quincaillerie");
        chezLeVoisin.setIdEntreprise(voisin);
        categoryRepository.save(chezLeVoisin);

        assertThat(etape(demarrage.etat(), EtapeDemarrage.CATEGORIES).isFaite()).isFalse();
    }

    @Test
    @Transactional
    void fermer_l_assistant_se_retient_et_se_defait() {
        assertThat(demarrage.masquer(true).isMasque()).isTrue();
        assertThat(entrepriseRepository.findById(idEntreprise).orElseThrow().isDemarrageMasque()).isTrue();

        assertThat(demarrage.masquer(false).isMasque()).isFalse();
    }
}
