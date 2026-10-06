package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeClient;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Une ligne porte l'entreprise de son document, par quelque chemin qu'elle soit creee. */
class LignesCloisonneesTest extends AbstractIntegrationTest {

    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private MvtStkService mvtStkService;
    @Autowired
    private VenteService venteService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private CommandeClientService commandeClientService;
    @Autowired
    private LigneVenteRepository ligneVenteRepository;
    @Autowired
    private LigneCmndeClientRepository ligneCmndeClientRepository;

    private Long idEntreprise;
    private Long idArticle;

    @BeforeEach
    void unCommerce() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Commerce " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "g@exemple.test", "x", idEntreprise,
                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        principal.setIdsSites(Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        CategoryDto rayon = categoryService.save(CategoryDto.builder()
                .codeCategorie("CAT-" + UUID.randomUUID()).designation("Divers").build());
        idArticle = articleService.save(ArticleDto.builder()
                .codeArticle("ART-" + UUID.randomUUID()).designation("Savon")
                .prixUnitaireHt(new BigDecimal("500")).category(rayon).build()).getId();
        mvtStkService.entreeStock(MvtStkDto.builder().article(ArticleDto.builder().Id(idArticle).build())
                .quantite(new BigDecimal("20")).build());
    }

    @AfterEach
    void oublier() {
        SecurityContextHolder.clearContext();
    }

    private List<LigneVenteDto> uneLigne() {
        return List.of(LigneVenteDto.builder().article(ArticleDto.builder().Id(idArticle).build())
                .quantite(BigDecimal.ONE).build());
    }

    @Test
    void la_vente_au_comptoir_et_la_vente_synchronisee_rangent_leurs_lignes_dans_l_entreprise() {
        VenteDto comptoir = venteService.save(VenteDto.builder().code("V-" + UUID.randomUUID()).ligneVente(uneLigne()).build());
        VenteDto horsLigne = venteService.synchroniser(VenteDto.builder().code("V-" + UUID.randomUUID())
                .referenceClient(UUID.randomUUID().toString())
                .datevente(Instant.now().minus(1, ChronoUnit.HOURS))
                .ligneVente(uneLigne()).build());

        assertThat(ligneVenteRepository.findAllByVenteId(comptoir.getId()))
                .extracting(LigneVente::getIdEntreprise).containsOnly(idEntreprise);
        assertThat(ligneVenteRepository.findAllByVenteId(horsLigne.getId()))
                .extracting(LigneVente::getIdEntreprise).containsOnly(idEntreprise);
    }

    @Test
    void les_lignes_saisies_avec_la_commande_client_rangent_aussi_leur_entreprise() {
        ClientDto client = clientService.save(ClientDto.builder().nom("Ndi").prenoms("Luc")
                .mail("luc" + UUID.randomUUID() + "@exemple.test").numTel("690000030").build());
        CommandeClientDto commande = commandeClientService.save(CommandeClientDto.builder()
                .code("CC-" + UUID.randomUUID()).client(client)
                .ligneCmndeClients(List.of(LigneCommandeClientDto.builder()
                        .article(ArticleDto.builder().Id(idArticle).build())
                        .quantite(new BigDecimal("2")).build()))
                .build());

        assertThat(ligneCmndeClientRepository.findAllByCommandeClientId(commande.getId()))
                .extracting(LigneCmndeClient::getIdEntreprise).containsOnly(idEntreprise);
    }
}
