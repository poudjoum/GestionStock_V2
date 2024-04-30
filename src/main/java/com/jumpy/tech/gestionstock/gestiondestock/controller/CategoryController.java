package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.CategoryControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.CategoryService;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
public class CategoryController implements CategoryControllerApi {
    private CategoryService catService;

    public CategoryController(CategoryService catS){
         this.catService=catS;
     }

    @Override
    public CategoryDto save(ArticleDto dto) {
        return null;
    }

    @Override
    public CategoryDto findById(Long id) {
        return null;
    }

    @Override
    public CategoryDto findByCodeCat(String codeArticle) {
        return null;
    }

    @Override
    public List<CategoryDto> findAll() {
        return List.of();
    }

    @Override
    public void delete(Long id) {

    }
}
