package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.*;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CommandeFourRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FournisseurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeFourRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeFourService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.CommandFourValidator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    public CommandeFourServiceImpl(CommandeFourRepository commandeFourRepository, ArticleRepository articleRepository,
                                   LigneCmndeFourRepository ligneCmndeFourRepository, FournisseurRepository fournisseurRepository){
        this.commandeFourRepository=commandeFourRepository;
        this.articleRepository=articleRepository;
        this.fournisseurRepository=fournisseurRepository;
        this.ligneCmndeFourRepository=ligneCmndeFourRepository;
    }

    @Override
    public CommandeFourDto save(CommandeFourDto dto) {
        List<String> errors= CommandFourValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Commande Fournisseur n'est pas valide {}",dto);
            throw new InvalidEntityException("La commande fournisseur n'est pas valide", ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID,errors);
        }
        Optional<Fournisseur> fournisseur=fournisseurRepository.findById(dto.getFournisseur().getId());
        if(fournisseur.isEmpty()){
            log.warn("Fournisseur with Id {} was not in database",dto.getFournisseur().getId());
            throw new EntityNotFoundException("Aucun fournisseur avec l'ID = "+dto.getFournisseur().getId()+" n'est disponible dans la base de données");
        }
        List<String> articleErrors=new ArrayList<>();
        if(dto.getLigneCmndeFournisseur()!=null){
            dto.getLigneCmndeFournisseur().forEach(ligCmdClt->{
                if(ligCmdClt.getArticle()!=null){
                    Optional<Article>article=articleRepository.findById(ligCmdClt.getArticle().getId());
                    if(article.isEmpty()){
                        articleErrors.add("L' article avec l'ID = "+ligCmdClt.getArticle().getId()+" n'existe pas");
                    }
                }else{
                    articleErrors.add("Impossible d'enregistrer une commande avec un article null");
                }

            });
        }
        if(!articleErrors.isEmpty()){
            log.error("");
            throw new InvalidEntityException("L'article n'existe pas dans la base de donnees",ErrorCodes.ARTICLE_NOT_FOUND,articleErrors);
        }
        CommandeFour saveCmndFour=commandeFourRepository.save(CommandeFourDto.toEntity(dto));
        if(dto.getLigneCmndeFournisseur()!=null) {
            dto.getLigneCmndeFournisseur().forEach(ligCmdFour -> {
                LigneCmndeFournisseur ligneCmndeFour = LigneCmndeFournisseurDto.toEntity(ligCmdFour);
                ligneCmndeFour.setCommandeFournisseur(saveCmndFour);
                ligneCmndeFourRepository.save(ligneCmndeFour);
            });
        }
        return CommandeFourDto.fromEntity(saveCmndFour);
    }

    @Override
    public CommandeFourDto findById(Long id) {
        if(id==null){
            log.error("Commande client Id is null");
            return null;
        }
        return  commandeFourRepository.findById(id)
                .map(CommandeFourDto::fromEntity)
                .orElseThrow(()->new EntityNotFoundException("Aucune Commande avec l'id {}"+id,ErrorCodes.COMMANDE_FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public CommandeFourDto findByCode(String code) {
        if(!StringUtils.hasLength(code)){
            log.error("Commande Fournisseur Id is Null");
            return null;
        }
        return commandeFourRepository.findCommandeFourByCode(code)
                .map(CommandeFourDto::fromEntity)
                .orElseThrow(()->new EntityNotFoundException("Aucune Commande Fournisseur avec le code {}"+ code+" n'a été trouver ",ErrorCodes.COMMANDE_FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public List<CommandeFourDto> findAll() {
        return commandeFourRepository.findAll().stream()
                .map(CommandeFourDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        if(id==null){
            log.error("Commande Client ID is null");
        }
        commandeFourRepository.deleteById(id);
    }
}
