package com.jumpy.tech.gestionstock.gestiondestock.demarrage;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Adresse;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CategoryRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.MvtStkRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * L'assistant de demarrage : ce qu'il reste a faire a un commerce qu'on vient d'inscrire avant de
 * vendre.
 *
 * Rien ne se coche a la main. Chaque etape se lit dans ce que le commerce contient : une categorie
 * existe ou non, une vente a eu lieu ou non. Un gerant qui importe son catalogue par un classeur,
 * sans passer par l'assistant, le voit donc avancer quand meme — et un assistant qui dirait
 * « fait » d'une etape que les donnees dementent serait pire que pas d'assistant du tout.
 *
 * L'entreprise vient du jeton, jamais d'un identifiant recu, comme pour `/entreprises/mienne`.
 */
@Service
public class Demarrage {

    private final Cloisonnement cloisonnement;
    private final EntrepriseRepository entreprises;
    private final CategoryRepository categories;
    private final ArticleRepository articles;
    private final MvtStkRepository mouvements;
    private final UtilisateurRepository comptes;
    private final VenteRepository ventes;

    public Demarrage(Cloisonnement cloisonnement, EntrepriseRepository entreprises,
                     CategoryRepository categories, ArticleRepository articles,
                     MvtStkRepository mouvements, UtilisateurRepository comptes,
                     VenteRepository ventes) {
        this.cloisonnement = cloisonnement;
        this.entreprises = entreprises;
        this.categories = categories;
        this.articles = articles;
        this.mouvements = mouvements;
        this.comptes = comptes;
        this.ventes = ventes;
    }

    @Transactional(readOnly = true)
    public DemarrageDto etat() {
        return etatDe(laMienne());
    }

    /** Ferme l'assistant, ou le rouvre. Rend l'etat tel qu'il est apres. */
    @Transactional
    public DemarrageDto masquer(boolean masque) {
        Entreprise entreprise = laMienne();
        entreprise.setDemarrageMasque(masque);
        return etatDe(entreprises.save(entreprise));
    }

    private DemarrageDto etatDe(Entreprise entreprise) {
        Long id = entreprise.getId();

        long nbCategories = categories.countByIdEntreprise(id);
        long nbArticles = articles.countByIdEntreprise(id);
        // Le gerant lui-meme est un compte : l'equipe commence au deuxieme.
        long nbCollaborateurs = Math.max(0, comptes.countByEntrepriseId(id) - 1);

        List<DemarrageDto.EtapeDto> etapes = List.of(
                etape(EtapeDemarrage.IDENTITE, adresseRenseignee(entreprise.getAdresse()), false, null),
                etape(EtapeDemarrage.CATEGORIES, nbCategories > 0, false, nbCategories),
                etape(EtapeDemarrage.ARTICLES, nbArticles > 0, false, nbArticles),
                etape(EtapeDemarrage.STOCK, mouvements.existsByIdEntreprise(id), false, null),
                etape(EtapeDemarrage.EQUIPE, nbCollaborateurs > 0, true, nbCollaborateurs),
                etape(EtapeDemarrage.PREMIERE_VENTE, ventes.existsByIdEntrepriseAndAnnuleeFalse(id), false, null));

        return DemarrageDto.builder()
                .etapes(etapes)
                .termine(etapes.stream().allMatch(e -> e.isFaite() || e.isFacultative()))
                .masque(entreprise.isDemarrageMasque())
                .build();
    }

    private Entreprise laMienne() {
        Long id = cloisonnement.entrepriseCourante();
        if (id == null) {
            throw new EntityNotFoundException("Ce compte n'est rattaché à aucune entreprise",
                    ErrorCodes.ENTREPRISE_NOT_FOUND);
        }
        return entreprises.findById(id).orElseThrow(() -> new EntityNotFoundException(
                "Aucune entreprise avec l'identifiant " + id + " n'a été trouvée",
                ErrorCodes.ENTREPRISE_NOT_FOUND));
    }

    /**
     * L'inscription n'exige que le nom et le telephone : l'adresse est la mention du ticket qui
     * manque le plus souvent a un commerce qui vient d'arriver.
     */
    private static boolean adresseRenseignee(Adresse adresse) {
        return adresse != null
                && (StringUtils.hasText(adresse.getAdresse1()) || StringUtils.hasText(adresse.getVille()));
    }

    private static DemarrageDto.EtapeDto etape(EtapeDemarrage code, boolean faite, boolean facultative, Long nombre) {
        return DemarrageDto.EtapeDto.builder()
                .code(code).faite(faite).facultative(facultative).nombre(nombre).build();
    }
}
