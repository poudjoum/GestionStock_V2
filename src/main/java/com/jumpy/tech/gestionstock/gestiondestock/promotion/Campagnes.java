package com.jumpy.tech.gestionstock.gestiondestock.promotion;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CampagneDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.PromotionArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Campagne;
import com.jumpy.tech.gestionstock.gestiondestock.entities.PromotionArticle;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeRemise;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CampagneRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.PromotionArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Les campagnes de promotion du magasin, telles que son gerant les prepare.
 *
 * L'entreprise vient du compte connecte, jamais de la requete : on ne met pas en promotion les
 * articles du voisin.
 */
@Service
public class Campagnes {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    /** Une image encodee de deux mega-octets : au-dela, le front ne l'a pas reduite. */
    static final int IMAGE_MAX = 2_000_000;

    private final CampagneRepository campagnes;
    private final PromotionArticleRepository promotions;
    private final ArticleRepository articles;
    private final Cloisonnement cloisonnement;
    private final Calendrier calendrier;

    public Campagnes(CampagneRepository campagnes, PromotionArticleRepository promotions,
                     ArticleRepository articles, Cloisonnement cloisonnement, Calendrier calendrier) {
        this.campagnes = campagnes;
        this.promotions = promotions;
        this.articles = articles;
        this.cloisonnement = cloisonnement;
        this.calendrier = calendrier;
    }

    @Transactional(readOnly = true)
    public List<CampagneDto> lister() {
        LocalDate aujourdhui = calendrier.aujourdhui();
        return campagnes.findAllByIdEntrepriseOrderByDateDebutDesc(entreprise()).stream()
                .map(c -> CampagneDto.de(c, aujourdhui))
                .toList();
    }

    @Transactional(readOnly = true)
    public CampagneDto lire(Long id) {
        return CampagneDto.de(mienne(id), calendrier.aujourdhui());
    }

    /**
     * Les promotions qui valent aujourd'hui, pour la caisse.
     *
     * Elle les garde avec son catalogue pour vendre hors ligne, et chacune porte sa date de fin :
     * une caisse restee sans reseau ne doit pas continuer a les appliquer le lendemain.
     */
    @Transactional(readOnly = true)
    public List<PromotionArticleDto> promotionsEnCours() {
        return promotions.enCours(entreprise(), calendrier.aujourdhui()).stream()
                .map(PromotionArticleDto::de)
                .toList();
    }

    @Transactional
    public CampagneDto creer(CampagneDto dto) {
        LocalDate aujourdhui = calendrier.aujourdhui();
        valider(dto);
        if (dto.getDateFin().isBefore(aujourdhui)) {
            throw invalide("Une campagne ne peut pas se terminer dans le passé");
        }
        Campagne campagne = new Campagne();
        campagne.setIdEntreprise(entreprise());
        ecrire(campagne, dto);
        return CampagneDto.de(campagnes.save(campagne), aujourdhui);
    }

    /**
     * Corrige une campagne qui n'est pas encore finie.
     *
     * Une campagne terminee ou arretee ne se reecrit plus : des tickets et des factures citent ses
     * prix, et la changer apres coup ferait mentir ce que le client a sur son papier.
     */
    @Transactional
    public CampagneDto modifier(Long id, CampagneDto dto) {
        LocalDate aujourdhui = calendrier.aujourdhui();
        Campagne campagne = mienne(id);
        CampagneDto.Statut statut = CampagneDto.statut(campagne, aujourdhui);
        if (statut == CampagneDto.Statut.TERMINEE || statut == CampagneDto.Statut.ARRETEE) {
            throw invalide("Une campagne terminée ou arrêtée ne se modifie plus");
        }
        valider(dto);
        if (dto.getDateFin().isBefore(aujourdhui)) {
            throw invalide("Une campagne ne peut pas se terminer dans le passé");
        }
        // Une campagne commencee ne recule pas son debut : des ventes ont deja eu lieu a ses prix,
        // et les avancer dans le temps en ferait des ventes hors campagne.
        if (statut == CampagneDto.Statut.EN_COURS && !dto.getDateDebut().equals(campagne.getDateDebut())) {
            throw invalide("Une campagne commencée garde sa date de début");
        }
        ecrire(campagne, dto);
        return CampagneDto.de(campagnes.save(campagne), aujourdhui);
    }

    /** Arrete une campagne avant son terme : les prix reprennent aussitot. */
    @Transactional
    public CampagneDto arreter(Long id) {
        Campagne campagne = mienne(id);
        campagne.setArretee(true);
        return CampagneDto.de(campagnes.save(campagne), calendrier.aujourdhui());
    }

    private void ecrire(Campagne campagne, CampagneDto dto) {
        campagne.setTitre(dto.getTitre().trim());
        campagne.setMessage(StringUtils.hasText(dto.getMessage()) ? dto.getMessage().trim() : null);
        campagne.setImage(StringUtils.hasText(dto.getImage()) ? dto.getImage() : null);
        campagne.setDateDebut(dto.getDateDebut());
        campagne.setDateFin(dto.getDateFin());

        List<PromotionArticleDto> demandees = dto.getPromotions() == null ? List.of() : dto.getPromotions();
        Map<Long, Article> parId = articlesDuMagasin(demandees);
        verifierChevauchements(campagne, dto, parId.keySet());

        // On remplace la liste en place : la collection est suivie par Hibernate, et
        // `orphanRemoval` efface les promotions retirees.
        Map<Long, PromotionArticle> existantes = campagne.getPromotions().stream()
                .collect(Collectors.toMap(p -> p.getArticle().getId(), p -> p));
        campagne.getPromotions().clear();
        for (PromotionArticleDto demande : demandees) {
            PromotionArticle promotion = Optional.ofNullable(existantes.get(demande.getIdArticle()))
                    .orElseGet(PromotionArticle::new);
            promotion.setCampagne(campagne);
            promotion.setArticle(parId.get(demande.getIdArticle()));
            promotion.setIdEntreprise(campagne.getIdEntreprise());
            promotion.setTypeRemise(demande.getTypeRemise());
            promotion.setValeur(demande.getValeur());
            campagne.getPromotions().add(promotion);
        }
    }

    /** Les articles demandes, charges, du magasin, chacun avec une reduction qui a un sens. */
    private Map<Long, Article> articlesDuMagasin(List<PromotionArticleDto> demandees) {
        List<String> erreurs = new ArrayList<>();
        Map<Long, Article> parId = new HashMap<>();
        Set<Long> vus = new HashSet<>();
        for (PromotionArticleDto demande : demandees) {
            if (demande == null || demande.getIdArticle() == null) {
                erreurs.add("Une promotion désigne un article");
                continue;
            }
            if (!vus.add(demande.getIdArticle())) {
                erreurs.add("L'article " + demande.getIdArticle() + " figure deux fois dans la campagne");
                continue;
            }
            Article article = articles.findById(demande.getIdArticle())
                    .filter(a -> !cloisonnement.filtre()
                            || Objects.equals(a.getIdEntreprise(), cloisonnement.entrepriseCourante()))
                    .orElse(null);
            if (article == null) {
                erreurs.add("Aucun article avec l'identifiant " + demande.getIdArticle());
                continue;
            }
            String nom = article.getDesignation();
            if (demande.getTypeRemise() == null || demande.getValeur() == null || demande.getValeur().signum() <= 0) {
                erreurs.add(nom + " : la réduction doit être un pourcentage ou un prix, positif");
            } else if (demande.getTypeRemise() == TypeRemise.POURCENTAGE && demande.getValeur().compareTo(CENT) >= 0) {
                erreurs.add(nom + " : une réduction se donne entre 0 et 100 %");
            } else if (demande.getTypeRemise() == TypeRemise.PRIX_FIXE
                    && (article.getPrixUnitaire() == null || demande.getValeur().compareTo(article.getPrixUnitaire()) >= 0)) {
                erreurs.add(nom + " : le prix promotionnel doit être inférieur au prix normal ("
                        + article.getPrixUnitaire() + " F HT)");
            }
            parId.put(article.getId(), article);
        }
        if (!erreurs.isEmpty()) {
            throw new InvalidEntityException("La campagne n'est pas valide", ErrorCodes.CAMPAGNE_NOT_VALID, erreurs);
        }
        return parId;
    }

    /** Un article n'a qu'un prix promotionnel a la fois. */
    private void verifierChevauchements(Campagne campagne, CampagneDto dto, Set<Long> idArticles) {
        if (idArticles.isEmpty()) {
            return;
        }
        Long idCampagne = campagne.getId() == null ? -1L : campagne.getId();
        List<String> erreurs = promotions.chevauchements(campagne.getIdEntreprise(), idCampagne,
                        dto.getDateDebut(), dto.getDateFin(), idArticles).stream()
                .map(p -> p.getArticle().getDesignation() + " est déjà en promotion dans « "
                        + p.getCampagne().getTitre() + " » du " + p.getCampagne().getDateDebut()
                        + " au " + p.getCampagne().getDateFin())
                .toList();
        if (!erreurs.isEmpty()) {
            throw new InvalidEntityException("Des articles sont déjà en promotion sur cette période",
                    ErrorCodes.CAMPAGNE_NOT_VALID, erreurs);
        }
    }

    static void valider(CampagneDto dto) {
        List<String> erreurs = new ArrayList<>();
        if (dto == null) {
            throw invalide("La campagne est vide");
        }
        if (!StringUtils.hasText(dto.getTitre())) {
            erreurs.add("Une campagne porte un titre");
        } else if (dto.getTitre().trim().length() > 120) {
            erreurs.add("Le titre ne dépasse pas 120 caractères");
        }
        if (dto.getMessage() != null && dto.getMessage().trim().length() > 1000) {
            erreurs.add("Le message ne dépasse pas 1000 caractères");
        }
        if (dto.getImage() != null && dto.getImage().length() > IMAGE_MAX) {
            erreurs.add("L'image est trop lourde");
        }
        if (dto.getDateDebut() == null || dto.getDateFin() == null) {
            erreurs.add("Une campagne a une date de début et une date de fin");
        } else if (dto.getDateFin().isBefore(dto.getDateDebut())) {
            erreurs.add("La date de fin suit la date de début");
        }
        if (!erreurs.isEmpty()) {
            throw new InvalidEntityException("La campagne n'est pas valide", ErrorCodes.CAMPAGNE_NOT_VALID, erreurs);
        }
    }

    private Campagne mienne(Long id) {
        Campagne campagne = campagnes.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aucune campagne avec l'identifiant " + id,
                        ErrorCodes.CAMPAGNE_NOT_FOUND));
        if (!Objects.equals(campagne.getIdEntreprise(), entreprise())) {
            // 404 et non 403 : on ne confirme pas au voisin que la campagne existe.
            throw new EntityNotFoundException("Aucune campagne avec l'identifiant " + id,
                    ErrorCodes.CAMPAGNE_NOT_FOUND);
        }
        return campagne;
    }

    private Long entreprise() {
        Long id = cloisonnement.entrepriseCourante();
        if (id == null) {
            throw new EntityNotFoundException("Ce compte n'est rattaché à aucune entreprise",
                    ErrorCodes.ENTREPRISE_NOT_FOUND);
        }
        return id;
    }

    private static InvalidEntityException invalide(String message) {
        return new InvalidEntityException(message, ErrorCodes.CAMPAGNE_NOT_VALID, List.of(message));
    }
}
