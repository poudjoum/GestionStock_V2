package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MesSitesDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.SiteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ArticleSite;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeSite;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleSiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.SiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.SiteService;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SiteServiceImpl implements SiteService {

    private final SiteRepository siteRepository;
    private final ArticleRepository articleRepository;
    private final ArticleSiteRepository articleSiteRepository;
    private final MvtStkRepository mvtStkRepository;
    private final SiteCourant siteCourant;
    private final Cloisonnement cloisonnement;

    public SiteServiceImpl(SiteRepository siteRepository, ArticleRepository articleRepository,
                           ArticleSiteRepository articleSiteRepository, MvtStkRepository mvtStkRepository,
                           SiteCourant siteCourant, Cloisonnement cloisonnement) {
        this.siteRepository = siteRepository;
        this.articleRepository = articleRepository;
        this.articleSiteRepository = articleSiteRepository;
        this.mvtStkRepository = mvtStkRepository;
        this.siteCourant = siteCourant;
        this.cloisonnement = cloisonnement;
    }

    @Override
    public List<SiteDto> sites() {
        return siteRepository.findAllByIdEntrepriseOrderByPrincipalDescNomAsc(entreprise()).stream()
                .map(SiteDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public MesSitesDto mesSites() {
        Long entreprise = entreprise();
        // Le site principal existe toujours : le demander le cree s'il manquait.
        siteCourant.principal(entreprise);
        List<SiteDto> siens = siteRepository.findAllByIdEntrepriseOrderByPrincipalDescNomAsc(entreprise).stream()
                .filter(Site::isActif)
                .filter(siteCourant::peutVoir)
                .map(SiteDto::fromEntity)
                .collect(Collectors.toList());
        return MesSitesDto.builder()
                .sites(siens)
                .actif(siteCourant.idSite())
                .tousLesSites(siteCourant.voitTousLesSites())
                .build();
    }

    @Override
    @Transactional
    public SiteDto creer(SiteDto dto) {
        Site site = new Site();
        site.setIdEntreprise(entreprise());
        appliquer(site, dto);
        return SiteDto.fromEntity(siteRepository.save(site));
    }

    @Override
    @Transactional
    public SiteDto modifier(Long id, SiteDto dto) {
        Site site = site(id);
        if (!site.isActif()) {
            throw new InvalidEntityException("Un site fermé ne se modifie plus", ErrorCodes.SITE_NOT_VALID);
        }
        TypeSite avant = site.getType();
        appliquer(site, dto);
        // Le site principal recoit les ventes de qui n'a pas de site : il doit pouvoir vendre.
        if (site.isPrincipal() && site.getType() != TypeSite.MAGASIN) {
            site.setType(avant);
            throw new InvalidEntityException("Le site principal est un magasin", ErrorCodes.SITE_NOT_VALID,
                    List.of("C'est lui qui vend pour les comptes sans site attribué"));
        }
        return SiteDto.fromEntity(siteRepository.save(site));
    }

    @Override
    @Transactional
    public SiteDto fermer(Long id) {
        Site site = site(id);
        if (site.isPrincipal()) {
            throw new InvalidEntityException("Le site principal ne se ferme pas", ErrorCodes.SITE_NOT_VALID);
        }
        List<Long> enStock = mvtStkRepository.articlesEnStockDansSite(id, TypeMvtStk.ENTREE);
        if (!enStock.isEmpty()) {
            throw new InvalidEntityException(
                    "« " + site.getNom() + " » a encore du stock sur " + enStock.size() + " article(s)",
                    ErrorCodes.SITE_NOT_VALID,
                    List.of("Transférez-le vers un autre site, ou corrigez-le par un inventaire, avant de fermer"));
        }
        site.setActif(false);
        log.info("Site {} ferme ({})", id, site.getNom());
        return SiteDto.fromEntity(siteRepository.save(site));
    }

    @Override
    @Transactional
    public void creerSitePrincipal(Long idEntreprise) {
        if (idEntreprise != null) {
            siteCourant.principal(idEntreprise);
        }
    }

    @Override
    @Transactional
    public void definirSeuil(Long idArticle, Long idSite, BigDecimal seuil) {
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + idArticle + " n'a été trouvé", ErrorCodes.ARTICLE_NOT_FOUND));
        cloisonnement.verifierAcces(article.getIdEntreprise(), "article", idArticle);
        Site site = siteCourant.accessible(idSite);
        if (seuil != null && seuil.signum() < 0) {
            throw new InvalidEntityException("Le seuil d'alerte ne peut pas être négatif", ErrorCodes.SITE_NOT_VALID);
        }
        ArticleSite ligne = articleSiteRepository.findByArticleIdAndSiteId(idArticle, idSite).orElseGet(() -> {
            ArticleSite neuve = new ArticleSite();
            neuve.setArticle(article);
            neuve.setSite(site);
            neuve.setIdEntreprise(article.getIdEntreprise());
            return neuve;
        });
        if (seuil == null) {
            // Plus de seuil propre : celui de l'article reprend la main.
            if (ligne.getId() != null) {
                articleSiteRepository.delete(ligne);
            }
            return;
        }
        ligne.setSeuilAlerte(seuil);
        articleSiteRepository.save(ligne);
    }

    private void appliquer(Site site, SiteDto dto) {
        List<String> erreurs = new ArrayList<>();
        String nom = dto == null || dto.getNom() == null ? "" : dto.getNom().trim();
        if (!StringUtils.hasText(nom) || nom.length() > 80) {
            erreurs.add("Le nom du site est obligatoire, 80 caractères au plus");
        } else if (siteRepository.findAllByIdEntrepriseOrderByPrincipalDescNomAsc(site.getIdEntreprise()).stream()
                .filter(Site::isActif)
                .filter(autre -> !Objects.equals(autre.getId(), site.getId()))
                .anyMatch(autre -> autre.getNom().equalsIgnoreCase(nom))) {
            erreurs.add("Un site s'appelle déjà « " + nom + " »");
        }
        if (dto == null || dto.getType() == null) {
            erreurs.add("Dites s'il s'agit d'un magasin ou d'un entrepôt");
        }
        if (!erreurs.isEmpty()) {
            throw new InvalidEntityException("Le site n'est pas valide", ErrorCodes.SITE_NOT_VALID, erreurs);
        }
        site.setNom(nom);
        site.setType(dto.getType());
        site.setAdresse(StringUtils.hasText(dto.getAdresse()) ? dto.getAdresse().trim() : null);
        site.setTelephone(StringUtils.hasText(dto.getTelephone()) ? dto.getTelephone().trim() : null);
    }

    private Site site(Long id) {
        return siteRepository.findById(id)
                .filter(s -> Objects.equals(s.getIdEntreprise(), entreprise()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun site avec l'identifiant " + id + " n'a été trouvé", ErrorCodes.SITE_NOT_FOUND));
    }

    private Long entreprise() {
        Long entreprise = cloisonnement.entrepriseCourante();
        if (entreprise == null) {
            throw new InvalidEntityException("Ce compte n'est rattaché à aucune entreprise", ErrorCodes.SITE_NOT_VALID);
        }
        return entreprise;
    }
}
