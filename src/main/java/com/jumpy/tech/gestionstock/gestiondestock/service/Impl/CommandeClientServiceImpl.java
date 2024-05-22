package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeClient;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeClient;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CommandeClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeClientService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.CommandeClientValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j

public class CommandeClientServiceImpl implements CommandeClientService {

    private CommandeClientRepository commandeClientRepository;
    private ClientRepository clientRepository;
    private ArticleRepository articleRepository;
    private LigneCmndeClientRepository ligneCmndeClientRepository;
    public CommandeClientServiceImpl(CommandeClientRepository commandeClientRepository, ClientRepository clientRepository,ArticleRepository articleRepository,LigneCmndeClientRepository ligneCmndeClientRepository){
        this.commandeClientRepository=commandeClientRepository;
        this.articleRepository=articleRepository;
        this.clientRepository=clientRepository;
        this.ligneCmndeClientRepository=ligneCmndeClientRepository;
    }


    @Override
    public CommandeClientDto save(CommandeClientDto dto) {
        List<String> errors= CommandeClientValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Commande client n'est pas valide");
            throw new InvalidEntityException("La commande client n'est pas valide", ErrorCodes.COMMANDE_CLIENT_NOT_VALID,errors);
        }
        Optional<Client> client=clientRepository.findById(dto.getClient().getId());
        if(client.isEmpty()){
            log.warn("Client with Id {} was not in database",dto.getClient().getId());
            throw new EntityNotFoundException("Aucun client avec l'ID = "+dto.getClient().getId()+" n'est disponible dans la base de données");
        }
        List<String> articleErrors=new ArrayList<>();
        if(dto.getLigneCmndeClients()!=null){
            dto.getLigneCmndeClients().forEach(ligCmdClt->{
                if(ligCmdClt.getArticle()!=null){
                    Optional<Article>article=articleRepository.findById(ligCmdClt.getArticle().getId());
                    if(article.isEmpty()){
                        articleErrors.add("L' article avec l'ID = "+ligCmdClt.getArticle().getId()+" n'existe pas");
                    }
                }else{
                    articleErrors.add("Impossible d'enregistrer une commandeavec un article null");
                }

            });
        }
        if(!articleErrors.isEmpty()){
            log.error("");
            throw new InvalidEntityException("L'article n'existe pas dans la base de donnees",ErrorCodes.ARTICLE_NOT_FOUND,articleErrors);
        }
        CommandeClient saveCmndClt=commandeClientRepository.save(CommandeClientDto.toEntity(dto));
        if(dto.getLigneCmndeClients()!=null) {
            dto.getLigneCmndeClients().forEach(ligCmdClt -> {
                LigneCmndeClient ligneCmndeClient = LigneCommandeClientDto.toEntity(ligCmdClt);
                ligneCmndeClient.setCommandeClient(saveCmndClt);
                ligneCmndeClientRepository.save(ligneCmndeClient);
            });
        }
        return CommandeClientDto.fromEntity(saveCmndClt);
    }

    @Override
    public CommandeClientDto findById(Long id) {

        if(id==null){
            log.error("Commande client Id is null");
            return null;
        }
    return  commandeClientRepository.findById(id)
            .map(CommandeClientDto::fromEntity)
            .orElseThrow(()->new EntityNotFoundException("Aucune Commande avec l'id {}"+id,ErrorCodes.COMMANDE_CLIENT_NOT_FOUND));
    }

    @Override
    public CommandeClientDto findByCode(String code) {
        if(!StringUtils.hasLength(code)){
            log.error("Commande client Id is Null");
            return null;
        }
        return commandeClientRepository.findCommandeClientByCode(code)
                .map(CommandeClientDto::fromEntity)
                .orElseThrow(()->new EntityNotFoundException("Aucune Commande client avec le code {}"+ code+" n'a été trouver ",ErrorCodes.COMMANDE_CLIENT_NOT_FOUND));
    }

    @Override
    public List<CommandeClientDto> findAll() {
        return commandeClientRepository.findAll().stream()
                .map(CommandeClientDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        if(id==null){
            log.error("Commande Client ID is null");
        }
        commandeClientRepository.deleteById(id);
    }
}
