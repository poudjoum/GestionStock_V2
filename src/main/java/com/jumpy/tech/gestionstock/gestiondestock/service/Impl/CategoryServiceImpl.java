package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Category;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CategoryRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.CategoryService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.CategoryValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j

public class CategoryServiceImpl implements CategoryService {

    private CategoryRepository categoryRepository;
    private final Cloisonnement cloisonnement;
    public  CategoryServiceImpl(CategoryRepository cat, Cloisonnement cloisonnement){
        this.categoryRepository=cat;
        this.cloisonnement=cloisonnement;
    }

    private Category categorie(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune catégorie avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.CATEGORY_NOT_FOUND));
        cloisonnement.verifierAcces(category.getIdEntreprise(), "catégorie", id);
        return category;
    }
    @Override
    @Transactional
    public CategoryDto save(CategoryDto dto) {
        List<String> errors= CategoryValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Category not valid {}",dto);
            throw new InvalidEntityException("La catégorie n'est pas valide", ErrorCodes.CATEGORY_NOT_VALID,errors);
        }
        Category aEnregistrer = CategoryDto.toEntity(dto);
        if (aEnregistrer.getId() != null) {
            aEnregistrer.setIdEntreprise(categorie(aEnregistrer.getId()).getIdEntreprise());
        } else if (cloisonnement.filtre()) {
            aEnregistrer.setIdEntreprise(cloisonnement.entrepriseCourante());
        }
        return CategoryDto.fromEntity(categoryRepository.save(aEnregistrer));
    }

    @Override
    public CategoryDto findById(Long id) {
        if(id==null){
            log.error("Category Id is null");
            return null;
        }
        return CategoryDto.fromEntity(categorie(id));
    }

    @Override
    public List<CategoryDto> findAll() {
        return (cloisonnement.filtre()
                ? categoryRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante())
                : categoryRepository.findAll()).stream()
                .map(CategoryDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDto findByCode(String code) {
        if (!StringUtils.hasLength(code)) {
            log.error("Category Code is null");
            return null;
        }
        return (cloisonnement.filtre()
                ? categoryRepository.findCategoriesByCodeCatAndIdEntreprise(code, cloisonnement.entrepriseCourante())
                : categoryRepository.findCategoriesByCodeCat(code))
                .map(CategoryDto::fromEntity)
                .orElseThrow(()-> new EntityNotFoundException(
                        "Aucune catégorie avec le code "+code+" n'a été trouvée",
                        // Le code rendu etait ARTICLE_NOT_FOUND : une categorie introuvable
                        // s'annoncait au client comme un article introuvable.
                        ErrorCodes.CATEGORY_NOT_FOUND)
                );
    }


    @Override
    @Transactional
    public void delete(Long id){
        if(id==null){
            log.error("Categorie Id is null");
            return;
        }
        categoryRepository.delete(categorie(id));
    }
}
