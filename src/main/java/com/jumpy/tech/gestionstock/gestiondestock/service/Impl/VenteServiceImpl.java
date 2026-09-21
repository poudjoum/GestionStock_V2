package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
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
    private final MvtStkService mvtStkService;

    public VenteServiceImpl(VenteRepository venteRepository, ArticleRepository articleRepository,
                            LigneVenteRepository ligneVenteRepository, MvtStkService mvtStkService) {
        this.venteRepository = venteRepository;
        this.articleRepository = articleRepository;
        this.ligneVenteRepository = ligneVenteRepository;
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
            throw new InvalidEntityException("Vente is not valid", ErrorCodes.VENTE_NOT_VALID, errors);
        }

        List<LigneVenteDto> lignes = dto.getLigneVente() == null ? List.of() : dto.getLigneVente();
        if (lignes.isEmpty()) {
            throw new InvalidEntityException("Une vente sans ligne ne vend rien",
                    ErrorCodes.VENTE_NOT_VALID, List.of("Aucune ligne de vente fournie"));
        }

        List<String> articleErrors = new ArrayList<>();
        for (LigneVenteDto ligne : lignes) {
            if (ligne.getArticle() == null || ligne.getArticle().getId() == null) {
                articleErrors.add("Impossible d'enregistrer une vente avec un article null");
                continue;
            }
            Optional<Article> article = articleRepository.findById(ligne.getArticle().getId());
            if (article.isEmpty()) {
                articleErrors.add("Aucun Article avec l'ID " + ligne.getArticle().getId()
                        + " n'a ete trouve dans la base de donnees");
            }
        }
        if (!articleErrors.isEmpty()) {
            log.error("One or more article were not in Database, {}", articleErrors);
            // Les erreurs remontees etaient `errors`, la liste de validation, toujours vide a ce
            // stade : le client recevait un 400 sans savoir quel article posait probleme.
            throw new InvalidEntityException(
                    "Un ou plusieurs articles n'ont pas ete trouves dans la base de donnees",
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
                .idEntreprise(vente.getIdEntreprise() == null ? null : vente.getIdEntreprise().intValue())
                .build());
    }

    @Override
    public VenteDto findById(Long id) {
        if (id == null) {
            log.error("Vente id is null");
            throw new InvalidEntityException("Aucune Vente ne peut etre cherchee sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return venteRepository.findById(id)
                .map(VenteDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune Vente avec l'id = " + id + " n'a ete trouvee dans la base de donnees",
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
            throw new InvalidEntityException("Aucune Vente ne peut etre cherchee sans code",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return venteRepository.findVenteByCode(codeVente)
                .map(VenteDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune Vente avec le code = " + codeVente + " n'a ete trouvee dans la base de donnees",
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
