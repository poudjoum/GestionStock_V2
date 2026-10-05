package com.jumpy.tech.gestionstock.gestiondestock.site;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeSite;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.repository.SiteRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Le site sur lequel l'appelant travaille, a cette requete.
 *
 * Le front l'envoie dans l'en-tete {@value #ENTETE} — le site choisi dans le selecteur. Sans
 * en-tete, c'est le site par defaut du compte, puis le premier de ses sites, puis le site
 * principal : un poste qui ne connait pas encore les sites travaille donc au magasin principal,
 * comme avant.
 *
 * L'en-tete ne fait jamais foi seul : un caissier qui y ecrit le numero d'un autre magasin est
 * refuse, comme un appelant qui y ecrit celui d'un site du voisin.
 */
@Component
public class SiteCourant {

    public static final String ENTETE = "X-Site";

    /** Ceux qui voient tous les sites de l'entreprise, sans qu'on leur en attribue. */
    private static final List<ERole> TOUS_LES_SITES =
            List.of(ERole.ROLE_ADMIN, ERole.ROLE_MANAGER, ERole.ROLE_COMPTABLE, ERole.ROLE_SUPER_ADMIN);

    private final SiteRepository siteRepository;
    private final Cloisonnement cloisonnement;

    public SiteCourant(SiteRepository siteRepository, Cloisonnement cloisonnement) {
        this.siteRepository = siteRepository;
        this.cloisonnement = cloisonnement;
    }

    /**
     * Le site actif, ou {@code null} hors de toute entreprise — le super-administrateur, un
     * traitement interne. Les lectures portent alors sur tous les sites.
     */
    public Site site() {
        Long entreprise = cloisonnement.entrepriseCourante();
        if (entreprise == null) {
            return null;
        }
        Long demande = entete();
        if (demande != null) {
            return accessible(demande);
        }
        UserDetailsImpl compte = compte();
        if (compte != null && compte.getIdSiteDefaut() != null) {
            Site parDefaut = siteRepository.findById(compte.getIdSiteDefaut()).orElse(null);
            if (parDefaut != null && parDefaut.isActif() && peutVoir(parDefaut)) {
                return parDefaut;
            }
        }
        if (compte != null && !voitTousLesSites() && !compte.getIdsSites().isEmpty()) {
            return compte.getIdsSites().stream().sorted().map(siteRepository::findById)
                    .flatMap(java.util.Optional::stream).filter(Site::isActif).findFirst()
                    .orElseGet(() -> principal(entreprise));
        }
        return principal(entreprise);
    }

    /** L'identifiant du site actif, ou {@code null}. */
    public Long idSite() {
        Site site = site();
        return site == null ? null : site.getId();
    }

    /**
     * Ce site, a condition que l'appelant puisse y travailler.
     *
     * Un 404 et non un 403 pour un site qui n'est pas a lui, comme pour toute donnee cloisonnee :
     * repondre « interdit » confirmerait que le site existe.
     */
    public Site accessible(Long idSite) {
        Site site = siteRepository.findById(idSite)
                .filter(s -> Objects.equals(s.getIdEntreprise(), cloisonnement.entrepriseCourante())
                        || !cloisonnement.filtre())
                .filter(this::peutVoir)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun site avec l'identifiant " + idSite + " n'a été trouvé", ErrorCodes.SITE_NOT_FOUND));
        if (!site.isActif()) {
            throw new EntityNotFoundException("Le site « " + site.getNom() + " » est fermé", ErrorCodes.SITE_NOT_FOUND);
        }
        return site;
    }

    /** L'appelant travaille-t-il dans ce site ? */
    public boolean peutVoir(Site site) {
        if (!cloisonnement.filtre() || voitTousLesSites()) {
            return true;
        }
        UserDetailsImpl compte = compte();
        Set<Long> siens = compte == null ? Set.of() : compte.getIdsSites();
        return siens.isEmpty() ? site.isPrincipal() : siens.contains(site.getId());
    }

    /** L'administrateur, le gerant et le comptable regardent l'entreprise entiere. */
    public boolean voitTousLesSites() {
        return !cloisonnement.filtre() || TOUS_LES_SITES.stream().anyMatch(cloisonnement::aLeRole);
    }

    /**
     * Le site principal de l'entreprise, cree s'il manque : une entreprise sans site ne pourrait
     * rien stocker, et toutes en ont un depuis la migration des sites — celle-ci n'est qu'un filet.
     */
    public Site principal(Long idEntreprise) {
        return siteRepository.findByIdEntrepriseAndPrincipalTrue(idEntreprise).orElseGet(() -> {
            Site site = new Site();
            site.setIdEntreprise(idEntreprise);
            site.setNom("Magasin principal");
            site.setType(TypeSite.MAGASIN);
            site.setPrincipal(true);
            return siteRepository.save(site);
        });
    }

    private Long entete() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs)) {
            return null;
        }
        String valeur = attributs.getRequest().getHeader(ENTETE);
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valeur.trim());
        } catch (NumberFormatException illisible) {
            return null;
        }
    }

    private UserDetailsImpl compte() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl compte
                ? compte : null;
    }
}
