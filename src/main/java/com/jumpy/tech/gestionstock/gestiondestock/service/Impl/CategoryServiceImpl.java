package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CategoryRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.CategoryService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.CategoryValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j

public class CategoryServiceImpl implements CategoryService {

    private CategoryRepository categoryRepository;
    public  CategoryServiceImpl(CategoryRepository cat){
        this.categoryRepository=cat;
    }
    @Override
    public CategoryDto save(CategoryDto dto) {
        List<String> errors= CategoryValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Category not valid {}",dto);
            throw new InvalidEntityException("La Catégory n'est pas valide", ErrorCodes.CATEGORY_NOT_VALID,errors);
        }
        return CategoryDto.fromEntity(categoryRepository.save(CategoryDto.toEntity(dto)));
    }

    @Override
    public CategoryDto findById(Long id) {
        if(id==null){
            log.error("Category Id is null");
            return null;
        }
        return categoryRepository.findById(id)
                .map(CategoryDto::fromEntity).
                orElseThrow(()-> new EntityNotFoundException("Aucune Category avec l'Id= "+id +" n' a été trouvé dans la base de donnée",
                        ErrorCodes.CATEGORY_NOT_FOUND));
    }

    @Override
    public List<CategoryDto> findAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDto findByCode(String code) {
        if (!StringUtils.hasLength(code)) {
            log.error("Category Code is null");
            return null;
        }
        return categoryRepository.findCategoriesByCodeCat(code)
                .map(CategoryDto::fromEntity)
                .orElseThrow(()-> new EntityNotFoundException(
                        "Aucune Catégory avec le code "+code+ " n'a été trouvé dans la base de donnée",
                        ErrorCodes.ARTICLE_NOT_FOUND)
                );
    }


    @Override
    public void delete(Long id){
        if(id==null){
            log.error("Catégory Id is null");
            return;
        }
        categoryRepository.deleteById(id);
    }
}
