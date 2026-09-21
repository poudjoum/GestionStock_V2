package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FactureDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Facture;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneFacture;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneFactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.FactureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FactureServiceImpl implements FactureService {

    /**
     * Deux decimales, arrondi commercial. Le cadrage est fixe ici, en un seul endroit : un
     * arrondi applique differemment a la ligne et au total fait apparaitre des ecarts d'un franc
     * que personne n'arrive ensuite a expliquer.
     */
    private static final int DECIMALES = 2;
    private static final RoundingMode ARRONDI = RoundingMode.HALF_UP;
    private static final BigDecimal CENT = new BigDecimal("100");

    private final FactureRepository factureRepository;
    private final LigneFactureRepository ligneFactureRepository;
    private final VenteRepository venteRepository;
    private final LigneVenteRepository ligneVenteRepository;

    public FactureServiceImpl(FactureRepository factureRepository,
                              LigneFactureRepository ligneFactureRepository,
                              VenteRepository venteRepository,
                              LigneVenteRepository ligneVenteRepository) {
        this.factureRepository = factureRepository;
        this.ligneFactureRepository = ligneFactureRepository;
        this.venteRepository = venteRepository;
        this.ligneVenteRepository = ligneVenteRepository;
    }

    @Override
    @Transactional
    public FactureDto emettre(Long idVente) {
        Vente vente = vente(idVente);

        if (vente.isAnnulee()) {
            throw new InvalidEntityException("Une vente annulée ne se facture pas",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("On ne réclame pas le paiement d'une marchandise rendue"));
        }
        if (factureRepository.existsByVenteId(idVente)) {
            throw new InvalidEntityException("Cette vente est déjà facturée",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("La facture existante doit être annulée avant d'en émettre une autre"));
        }

        List<LigneVente> lignesVente = ligneVenteRepository.findAllByVenteId(idVente);
        if (lignesVente.isEmpty()) {
            throw new InvalidEntityException("Une vente sans ligne ne se facture pas",
                    ErrorCodes.VENTE_NOT_VALID);
        }

        Facture facture = new Facture();
        facture.setNumero(numeroSuivant());
        facture.setDateEmission(Instant.now());
        facture.setVente(vente);
        facture.setIdEntreprise(vente.getIdEntreprise());
        facture.setTotalHt(BigDecimal.ZERO);
        facture.setTotalTva(BigDecimal.ZERO);
        facture.setTotalTtc(BigDecimal.ZERO);
        Facture enregistree = factureRepository.save(facture);

        List<LigneFacture> lignes = new ArrayList<>();
        BigDecimal totalHt = BigDecimal.ZERO;
        BigDecimal totalTva = BigDecimal.ZERO;

        for (LigneVente ligneVente : lignesVente) {
            LigneFacture ligne = figer(ligneVente, enregistree);
            lignes.add(ligneFactureRepository.save(ligne));
            totalHt = totalHt.add(ligne.getMontantHt());
            totalTva = totalTva.add(ligne.getMontantTva());
        }

        // Les totaux somment les montants deja arrondis des lignes, et ne sont pas recalcules
        // depuis les quantites : c'est la seule facon que le total affiche corresponde a
        // l'addition des lignes que le client a sous les yeux.
        enregistree.setTotalHt(arrondi(totalHt));
        enregistree.setTotalTva(arrondi(totalTva));
        enregistree.setTotalTtc(arrondi(totalHt.add(totalTva)));

        log.info("Facture {} emise pour la vente {} : {} TTC",
                enregistree.getNumero(), idVente, enregistree.getTotalTtc());
        return FactureDto.avecLignes(factureRepository.save(enregistree), lignes);
    }

    /**
     * Recopie la ligne de vente dans la facture.
     *
     * Le prix vient de la ligne de vente — c'est celui auquel on a vendu, et non le prix courant
     * de l'article, qui a pu changer depuis. Le taux de TVA, lui, n'existe que sur l'article :
     * faute de mieux, il est pris la et fige ici meme, pour qu'une revision du taux ne reecrive
     * pas les factures passees.
     */
    private LigneFacture figer(LigneVente ligneVente, Facture facture) {
        Article article = ligneVente.getArticles();

        BigDecimal quantite = ligneVente.getQuantite() == null ? BigDecimal.ZERO : ligneVente.getQuantite();
        BigDecimal prixUnitaire = prixUnitaire(ligneVente, article);
        BigDecimal tauxTva = article == null || article.getTauxTva() == null
                ? BigDecimal.ZERO : article.getTauxTva();

        BigDecimal montantHt = arrondi(quantite.multiply(prixUnitaire));
        BigDecimal montantTva = arrondi(montantHt.multiply(tauxTva).divide(CENT, DECIMALES + 2, ARRONDI));

        LigneFacture ligne = new LigneFacture();
        ligne.setFacture(facture);
        ligne.setCodeArticle(article == null ? null : article.getCodeArticle());
        ligne.setDesignation(article == null ? null : article.getDesignation());
        ligne.setQuantite(quantite);
        ligne.setPrixUnitaireHt(prixUnitaire);
        ligne.setTauxTva(tauxTva);
        ligne.setMontantHt(montantHt);
        ligne.setMontantTva(montantTva);
        ligne.setMontantTtc(arrondi(montantHt.add(montantTva)));
        return ligne;
    }

    /**
     * Le prix de la ligne de vente fait foi. Il peut manquer — rien ne l'imposait jusqu'ici — et
     * le prix courant de l'article sert alors de repli, faute de meilleure source.
     */
    private BigDecimal prixUnitaire(LigneVente ligneVente, Article article) {
        if (ligneVente.getPrixUnitaire() != null) {
            return ligneVente.getPrixUnitaire();
        }
        if (article != null && article.getPrixUnitaire() != null) {
            return article.getPrixUnitaire();
        }
        return BigDecimal.ZERO;
    }

    /** Format `FA-2026-000012` : l'annee se lit sans ouvrir la facture, le rang ne se rejoue pas. */
    private String numeroSuivant() {
        long rang = factureRepository.prochainNumero();
        int annee = Instant.now().atZone(ZoneId.systemDefault()).getYear();
        return String.format("FA-%d-%06d", annee, rang);
    }

    private BigDecimal arrondi(BigDecimal montant) {
        return montant.setScale(DECIMALES, ARRONDI);
    }

    @Override
    public FactureDto findById(Long id) {
        Facture facture = facture(id);
        return FactureDto.avecLignes(facture, ligneFactureRepository.findAllByFactureId(id));
    }

    @Override
    public FactureDto findByNumero(String numero) {
        if (!StringUtils.hasLength(numero)) {
            throw new InvalidEntityException("Aucune facture ne peut être cherchée sans numéro",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        Facture facture = factureRepository.findByNumero(numero)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune facture portant le numéro " + numero + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
        return FactureDto.avecLignes(facture, ligneFactureRepository.findAllByFactureId(facture.getId()));
    }

    @Override
    public FactureDto findByVente(Long idVente) {
        Facture facture = factureRepository.findByVenteId(idVente)
                .orElseThrow(() -> new EntityNotFoundException(
                        "La vente " + idVente + " n'a pas été facturée",
                        ErrorCodes.VENTE_NOT_FOUND));
        return FactureDto.avecLignes(facture, ligneFactureRepository.findAllByFactureId(facture.getId()));
    }

    @Override
    public Page<FactureDto> findAll(Pageable pageable) {
        // Sans les lignes : une liste de factures affiche des totaux, pas le detail de chacune.
        return factureRepository.findAll(pageable).map(FactureDto::fromEntity);
    }

    @Override
    @Transactional
    public FactureDto annuler(Long id) {
        Facture facture = facture(id);
        if (facture.isAnnulee()) {
            throw new InvalidEntityException("Cette facture est déjà annulée",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        facture.setAnnulee(true);
        return FactureDto.avecLignes(factureRepository.save(facture),
                ligneFactureRepository.findAllByFactureId(id));
    }

    private Facture facture(Long id) {
        if (id == null) {
            throw new InvalidEntityException("Aucune facture ne peut être cherchée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return factureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune facture avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
    }

    private Vente vente(Long id) {
        if (id == null) {
            throw new InvalidEntityException("Aucune vente ne peut être facturée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return venteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
    }
}
