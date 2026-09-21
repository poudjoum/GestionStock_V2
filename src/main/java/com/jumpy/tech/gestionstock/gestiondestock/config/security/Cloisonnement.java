package com.jumpy.tech.gestionstock.gestiondestock.config.security;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Pour quelle entreprise travaille-t-on, et a quoi a-t-on droit de toucher.
 *
 * `id_entreprise` existait depuis le premier jour sur presque toutes les tables sans que rien ne
 * le renseigne ni ne filtre dessus : deux entreprises partageant cette base voyaient les articles,
 * les clients et les ventes l'une de l'autre. Cette classe est le seul endroit qui repond a la
 * question, pour que la reponse soit la meme partout.
 *
 * L'entreprise vient du compte connecte, jamais de la requete. Un identifiant d'entreprise fourni
 * par l'appelant serait une invitation a ecrire chez le voisin.
 */
@Component
public class Cloisonnement {

    /**
     * L'entreprise de l'appelant, ou `null` quand il n'y en a pas.
     *
     * Trois cas donnent `null`, et ils ne veulent pas dire la meme chose : le super-administrateur,
     * qui regarde au-dela d'une entreprise ; un compte sans entreprise, qui ne voit que les
     * donnees qui n'en ont pas ; et un appel sans authentification — un traitement interne ou un
     * test — ou il n'y a personne a qui demander. Les deux derniers se distinguent par
     * {@link #estAuthentifie()}.
     */
    public Long entrepriseCourante() {
        UserDetailsImpl utilisateur = utilisateur();
        return utilisateur == null ? null : utilisateur.getIdEntreprise();
    }

    /** Le super-administrateur n'est cloisonne par rien : il cree les entreprises. */
    public boolean estSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ERole.ROLE_SUPER_ADMIN.name()::equals);
    }

    public boolean estAuthentifie() {
        return utilisateur() != null;
    }

    /**
     * Faut-il filtrer les lectures ?
     *
     * Non pour le super-administrateur, et non hors de toute authentification : un traitement
     * interne ou un test qui appelle un service directement n'a pas d'entreprise, et lui rendre
     * une liste vide serait plus deroutant qu'utile. Toutes les routes HTTP exigeant un compte,
     * ce second cas ne se presente pas a travers l'API.
     */
    public boolean filtre() {
        return estAuthentifie() && !estSuperAdmin();
    }

    /**
     * Verifie qu'une donnee appartient bien a l'entreprise de l'appelant.
     *
     * Le refus prend la forme d'un 404 et non d'un 403 : repondre « interdit » confirmerait que
     * la donnee existe, et permettrait de deviner ce que le voisin possede en essayant des
     * identifiants.
     */
    public void verifierAcces(Long idEntrepriseDeLaDonnee, String quoi, Object identifiant) {
        if (!filtre()) {
            return;
        }
        if (!Objects.equals(entrepriseCourante(), idEntrepriseDeLaDonnee)) {
            throw new EntityNotFoundException(
                    "Aucun " + quoi + " avec l'identifiant " + identifiant + " n'a été trouvé",
                    ErrorCodes.ARTICLE_NOT_FOUND);
        }
    }

    private UserDetailsImpl utilisateur() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl principal)) {
            return null;
        }
        return principal;
    }
}
