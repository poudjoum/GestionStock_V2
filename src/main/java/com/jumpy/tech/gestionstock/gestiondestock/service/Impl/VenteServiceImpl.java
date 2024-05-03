package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.VenteService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.VenteValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VenteServiceImpl implements VenteService {
    private  VenteRepository venteRepository;
    private ArticleRepository articleRepository;
    private LigneVenteRepository ligneVenteRepository;

    public VenteServiceImpl(VenteRepository venteRepository,ArticleRepository articleRepository,LigneVenteRepository ligneVenteRepository) {
        this.venteRepository = venteRepository;
        this.articleRepository=articleRepository;
        this.ligneVenteRepository=ligneVenteRepository;
    }

    @Override
    public VenteDto save(VenteDto dto) {
        List<String> errors= VenteValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Vente not Valid {}",dto);
            throw new InvalidEntityException("Vente is not valid", ErrorCodes.VENTE_NOT_FOUND,errors);
        }
        List<String>articleErrors=new ArrayList<>();
        dto.getLigneVente().forEach(ligneVenteDto -> {
            Optional<Article> article=articleRepository.findById(ligneVenteDto.getArticle().getId());
            if(article.isEmpty()){

                articleErrors.add("Aucun Article avec l'ID "+ligneVenteDto.getArticle().getId()+" n'a été trouvé dans la Base de donnée ");
            }
        });
        if(!articleErrors.isEmpty()){
            log.error("One or more article were not in Database, {}",errors);
            throw new InvalidEntityException("Un ou plusieurs articles n'ont pas été trouvé dans la base de données",ErrorCodes.VENTE_NOT_VALID,errors);
        }
        Vente savedVente=venteRepository.save(VenteDto.toEntity(dto));
        return VenteDto.fromEntity(savedVente);
    }

    @Override
    public VenteDto findById(Long id) {
        if(id==null){
            log.error("Vente id is null");
        }
        Optional<Vente> vente=venteRepository.findById(id);
        VenteDto dto=VenteDto.fromEntity(vente.get());
        return Optional.of(dto).orElseThrow(()->
                new EntityNotFoundException("Aucune Vente avec l'id= "+id+" n'a été trouvé dans la base de donnée",ErrorCodes.VENTE_NOT_FOUND));
    }

    @Override
    public List<VenteDto> findAll() {
        return venteRepository.findAll().stream()
                .map(VenteDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public VenteDto findVenteByCode(String codeVente) {
        if(!StringUtils.hasLength(codeVente)){
            log.error("Le code Article est vide");
            return null;
        }
        Optional<Vente> vente= venteRepository.findVenteByCode(codeVente);
        VenteDto dto= VenteDto.fromEntity(vente.get());
        return Optional.of(dto).orElseThrow(()->
                new EntityNotFoundException("Aucun Article avec le code = "+codeVente +"n'a été trouvé dans la base de donnée",ErrorCodes.VENTE_NOT_FOUND));
    }


    @Override
    public void delete(Long id) {
        if(id==null){
            log.error("Article Id est null");
            return;
        }
        venteRepository.deleteById(id);
    }
}
