package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.*;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CommandeFourRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FournisseurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeFourRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeFourService;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.CommandFourValidator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommandeFourServiceImpl implements CommandeFourService {
    private CommandeFourRepository commandeFourRepository;
    private ArticleRepository articleRepository;
    private LigneCmndeFourRepository ligneCmndeFourRepository;
    private FournisseurRepository fournisseurRepository;
    private MvtStkService mvtStkService;

    public CommandeFourServiceImpl(CommandeFourRepository commandeFourRepository, ArticleRepository articleRepository,
                                   LigneCmndeFourRepository ligneCmndeFourRepository, FournisseurRepository fournisseurRepository,
                                   MvtStkService mvtStkService){
        this.commandeFourRepository=commandeFourRepository;
        this.articleRepository=articleRepository;
        this.fournisseurRepository=fournisseurRepository;
        this.ligneCmndeFourRepository=ligneCmndeFourRepository;
        this.mvtStkService=mvtStkService;
    }

    /**
     * Enregistre la commande, ses lignes, et l'entree en stock de chaque ligne.
     *
     * Le modele ne connait pas d'etat de commande : une commande fournisseur enregistree vaut donc
     * marchandise recue, et alimente le magasin. Le jour ou la commande recevra un cycle de vie
     * (commandee, livree, annulee), c'est au passage en « livree » que l'entree devra se faire ;
     * en attendre un aujourd'hui laisserait simplement le stock a zero pour toujours.
     */
    @Override
    @Transactional
    public CommandeFourDto save(CommandeFourDto dto) {
        List<String> errors= CommandFourValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Commande Fournisseur n'est pas valide {}",dto);
            throw new InvalidEntityException("La commande fournisseur n'est pas valide", ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID,errors);
        }
        Optional<Fournisseur> fournisseur=fournisseurRepository.findById(dto.getFournisseur().getId());
        if(fournisseur.isEmpty()){
            log.warn("Fournisseur with Id {} was not in database",dto.getFournisseur().getId());
            throw new EntityNotFoundException("Aucun fournisseur avec l'identifiant "+dto.getFournisseur().getId()+" n'a été trouvé",
                    ErrorCodes.FOURNISSEUR_NOT_FOUND);
        }
        List<String> articleErrors=new ArrayList<>();
        if(dto.getLigneCmndeFournisseur()!=null){
            dto.getLigneCmndeFournisseur().forEach(ligCmdClt->{
                if(ligCmdClt.getArticle()!=null){
                    Optional<Article>article=articleRepository.findById(ligCmdClt.getArticle().getId());
                    if(article.isEmpty()){
                        articleErrors.add("L'article avec l'identifiant "+ligCmdClt.getArticle().getId()+" n'existe pas");
                    }
                }else{
                    articleErrors.add("Impossible d'enregistrer une commande sans article");
                }

            });
        }
        if(!articleErrors.isEmpty()){
            log.error("");
            throw new InvalidEntityException("Un ou plusieurs articles de la commande n'existent pas",ErrorCodes.ARTICLE_NOT_FOUND,articleErrors);
        }
        CommandeFour saveCmndFour=commandeFourRepository.save(CommandeFourDto.toEntity(dto));
        if(dto.getLigneCmndeFournisseur()!=null) {
            dto.getLigneCmndeFournisseur().forEach(ligCmdFour -> {
                LigneCmndeFournisseur ligneCmndeFour = LigneCmndeFournisseurDto.toEntity(ligCmdFour);
                ligneCmndeFour.setCommandeFournisseur(saveCmndFour);
                ligneCmndeFourRepository.save(ligneCmndeFour);
                entrerEnStock(ligCmdFour);
            });
        }
        return CommandeFourDto.fromEntity(saveCmndFour);
    }

    private void entrerEnStock(LigneCmndeFournisseurDto ligne) {
        mvtStkService.entreeStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ligne.getArticle().getId()).build())
                .quantite(ligne.getQuantite())
                .build());
    }

    @Override
    public CommandeFourDto findById(Long id) {
        if(id==null){
            log.error("Commande client Id is null");
            return null;
        }
        return  commandeFourRepository.findById(id)
                .map(CommandeFourDto::fromEntity)
                // Le « {} » d'un journal SLF4J etait reste dans une concatenation.
                .orElseThrow(()->new EntityNotFoundException("Aucune commande fournisseur avec l'identifiant "+id+" n'a été trouvée",ErrorCodes.COMMANDE_FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public CommandeFourDto findByCode(String code) {
        if(!StringUtils.hasLength(code)){
            log.error("Commande Fournisseur Id is Null");
            return null;
        }
        return commandeFourRepository.findCommandeFourByCode(code)
                .map(CommandeFourDto::fromEntity)
                .orElseThrow(()->new EntityNotFoundException("Aucune commande fournisseur avec le code "+code+" n'a été trouvée",ErrorCodes.COMMANDE_FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public List<CommandeFourDto> findAll() {
        return commandeFourRepository.findAll().stream()
                .map(CommandeFourDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Commande Fournisseur ID is null");
            return;
        }
        commandeFourRepository.deleteById(id);
    }
}
