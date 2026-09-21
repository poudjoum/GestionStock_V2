package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import com.jumpy.tech.gestionstock.gestiondestock.service.VenteService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.VenteValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VenteServiceImpl implements VenteService {

    private final VenteRepository venteRepository;
    private final ArticleRepository articleRepository;
    private final LigneVenteRepository ligneVenteRepository;
    private final FactureRepository factureRepository;
    private final MvtStkService mvtStkService;

    public VenteServiceImpl(VenteRepository venteRepository, ArticleRepository articleRepository,
                            LigneVenteRepository ligneVenteRepository,
                            FactureRepository factureRepository, MvtStkService mvtStkService) {
        this.venteRepository = venteRepository;
        this.articleRepository = articleRepository;
        this.ligneVenteRepository = ligneVenteRepository;
        this.factureRepository = factureRepository;
        this.mvtStkService = mvtStkService;
    }

    /**
     * Enregistre la vente, ses lignes, et la sortie de stock de chaque ligne.
     *
     * Trois choses manquaient. Les lignes n'etaient jamais ecrites — `ligneVenteRepository` etait
     * injecte sans etre appele — donc une vente se resumait a un en-tete sans contenu. Aucun
     * mouvement de stock n'etait enregistre, donc vendre ne retirait rien du magasin. Et les deux
     * ecritures n'avaient pas de transaction commune.
     *
     * Le tout est maintenant dans une seule transaction : si une ligne manque de stock, la vente
     * entiere est annulee. Vendre la moitie d'un panier sans le dire serait pire que refuser.
     */
    @Override
    @Transactional
    public VenteDto save(VenteDto dto) {
        List<String> errors = VenteValidator.validate(dto);
        if (!errors.isEmpty()) {
            log.error("Vente not Valid {}", dto);
            // Le code rendu etait VENTE_NOT_FOUND pour une vente invalide.
            throw new InvalidEntityException("La vente n'est pas valide", ErrorCodes.VENTE_NOT_VALID, errors);
        }

        List<LigneVenteDto> lignes = dto.getLigneVente() == null ? List.of() : dto.getLigneVente();
        if (lignes.isEmpty()) {
            throw new InvalidEntityException("Une vente sans ligne ne vend rien",
                    ErrorCodes.VENTE_NOT_VALID, List.of("Aucune ligne de vente fournie"));
        }

        List<String> articleErrors = new ArrayList<>();
        for (LigneVenteDto ligne : lignes) {
            if (ligne.getArticle() == null || ligne.getArticle().getId() == null) {
                articleErrors.add("Impossible d'enregistrer une vente sans article");
                continue;
            }
            Optional<Article> article = articleRepository.findById(ligne.getArticle().getId());
            if (article.isEmpty()) {
                articleErrors.add("L'article avec l'identifiant " + ligne.getArticle().getId()
                        + " n'existe pas");
            }
        }
        if (!articleErrors.isEmpty()) {
            log.error("One or more article were not in Database, {}", articleErrors);
            // Les erreurs remontees etaient `errors`, la liste de validation, toujours vide a ce
            // stade : le client recevait un 400 sans savoir quel article posait probleme.
            throw new InvalidEntityException(
                    "Un ou plusieurs articles de la vente n'existent pas",
                    ErrorCodes.VENTE_NOT_VALID, articleErrors);
        }

        Vente savedVente = venteRepository.save(VenteDto.toEntity(dto));

        for (LigneVenteDto ligneDto : lignes) {
            LigneVente ligne = LigneVenteDto.toEntity(ligneDto);
            ligne.setVente(savedVente);
            ligneVenteRepository.save(ligne);
            sortirDuStock(ligneDto, savedVente);
        }

        return VenteDto.fromEntity(savedVente);
    }

    /**
     * La vente est le moment ou la marchandise quitte le magasin ; c'est donc ici, et pas a la
     * commande client, que le stock diminue. Une commande n'est qu'un engagement : tant qu'elle
     * n'est pas servie, rien n'est sorti des rayons.
     */
    private void sortirDuStock(LigneVenteDto ligne, Vente vente) {
        mvtStkService.sortieStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ligne.getArticle().getId()).build())
                .quantite(ligne.getQuantite())
                .motif(MotifMvtStk.VENTE)
                .idEntreprise(vente.getIdEntreprise() == null ? null : vente.getIdEntreprise().intValue())
                .build());
    }

    @Override
    public List<LigneVenteDto> lignes(Long idVente) {
        vente(idVente);
        return ligneVenteRepository.findAllByVenteId(idVente).stream()
                .map(LigneVenteDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VenteDto annuler(Long idVente) {
        Vente vente = venteModifiable(idVente);

        // Chaque ligne retourne en magasin. Le motif distingue cette entree d'une livraison :
        // sans lui, l'historique d'un article montrerait deux entrees identiques dont l'une
        // n'a jamais rien fait venir du fournisseur.
        ligneVenteRepository.findAllByVenteId(idVente).forEach(ligne ->
                mvtStkService.entreeStock(MvtStkDto.builder()
                        .article(ArticleDto.builder().Id(ligne.getArticles().getId()).build())
                        .quantite(ligne.getQuantite())
                        .motif(MotifMvtStk.ANNULATION_VENTE)
                        .build()));

        // La vente reste en base : une recette encaissee puis rendue doit pouvoir se retrouver.
        vente.setAnnulee(true);
        return VenteDto.fromEntity(venteRepository.save(vente));
    }

    @Override
    @Transactional
    public LigneVenteDto modifierQuantite(Long idVente, Long idLigne, BigDecimal quantite) {
        venteModifiable(idVente);
        LigneVente ligne = ligne(idVente, idLigne);
        BigDecimal nouvelle = quantiteValide(quantite);
        BigDecimal ancienne = ligne.getQuantite();

        // La marchandise est deja sortie : seule la difference se rattrape. Augmenter sort le
        // complement — et echoue si le magasin ne l'a pas, ce qui est bien le comportement
        // attendu ; diminuer remet la difference.
        int comparaison = nouvelle.compareTo(ancienne);
        if (comparaison > 0) {
            mvtStkService.sortieStock(mouvementDe(ligne, nouvelle.subtract(ancienne)));
        } else if (comparaison < 0) {
            mvtStkService.entreeStock(mouvementDe(ligne, ancienne.subtract(nouvelle)));
        }

        ligne.setQuantite(nouvelle);
        return LigneVenteDto.fromEntity(ligneVenteRepository.save(ligne));
    }

    @Override
    @Transactional
    public void retirerLigne(Long idVente, Long idLigne) {
        venteModifiable(idVente);
        LigneVente ligne = ligne(idVente, idLigne);
        mvtStkService.entreeStock(mouvementDe(ligne, ligne.getQuantite()));
        ligneVenteRepository.delete(ligne);
    }

    private MvtStkDto mouvementDe(LigneVente ligne, BigDecimal quantite) {
        return MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ligne.getArticles().getId()).build())
                .quantite(quantite)
                .motif(MotifMvtStk.CORRECTION_VENTE)
                .build();
    }

    private Vente vente(Long id) {
        if (id == null) {
            throw new InvalidEntityException("Aucune vente ne peut être cherchée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return venteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
    }

    /**
     * Une vente annulee a deja rendu sa marchandise : la corriger la rendrait une seconde fois.
     * Une vente facturee est figee pour une autre raison — la facture porte des montants remis au
     * client, et changer la vente sous elle la ferait mentir. Annuler la facture rouvre la vente.
     */
    private Vente venteModifiable(Long id) {
        Vente vente = vente(id);
        if (vente.isAnnulee()) {
            throw new InvalidEntityException("Une vente annulée ne se modifie plus",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("La vente " + id + " a déjà été annulée"));
        }
        factureRepository.findByVenteId(id)
                .filter(facture -> !facture.isAnnulee())
                .ifPresent(facture -> {
                    throw new InvalidEntityException("Une vente facturée ne se modifie plus",
                            ErrorCodes.VENTE_NOT_VALID,
                            List.of("La facture " + facture.getNumero()
                                    + " doit être annulée avant de reprendre cette vente"));
                });
        return vente;
    }

    private LigneVente ligne(Long idVente, Long idLigne) {
        LigneVente ligne = ligneVenteRepository.findById(idLigne)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune ligne avec l'identifiant " + idLigne + " n'a été trouvée",
                        ErrorCodes.LIGNE_VENTE_NOT_FOUND));
        if (ligne.getVente() == null || !idVente.equals(ligne.getVente().getId())) {
            throw new InvalidEntityException(
                    "La ligne " + idLigne + " n'appartient pas à la vente " + idVente,
                    ErrorCodes.LIGNE_VENTE_NOT_VALID);
        }
        return ligne;
    }

    private BigDecimal quantiteValide(BigDecimal quantite) {
        if (quantite == null || quantite.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEntityException(
                    "La quantité d'une ligne de vente doit être strictement positive",
                    ErrorCodes.LIGNE_VENTE_NOT_VALID);
        }
        return quantite;
    }

    @Override
    public VenteDto findById(Long id) {
        if (id == null) {
            log.error("Vente id is null");
            throw new InvalidEntityException("Aucune vente ne peut être cherchée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return venteRepository.findById(id)
                .map(VenteDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
    }

    @Override
    public List<VenteDto> findAll() {
        return venteRepository.findAll().stream()
                .map(VenteDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<VenteDto> findAll(Pageable pageable) {
        return venteRepository.findAll(pageable).map(VenteDto::fromEntity);
    }

    @Override
    public VenteDto findVenteByCode(String codeVente) {
        if (!StringUtils.hasLength(codeVente)) {
            log.error("Le code Vente est vide");
            throw new InvalidEntityException("Aucune vente ne peut être cherchée sans code",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return venteRepository.findVenteByCode(codeVente)
                .map(VenteDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec le code " + codeVente + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == null) {
            log.error("Vente Id est null");
            return;
        }
        venteRepository.deleteById(id);
    }
}
