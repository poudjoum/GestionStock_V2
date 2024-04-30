package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.CategoryControllerApi;
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
    public CategoryDto save(CategoryDto dto) {
        return catService.save(dto);
    }

    @Override
    public CategoryDto findById(Long id) {
        return catService.findById(id);
    }

    @Override
    public CategoryDto findByCodeCat(String codeCat) {
        return catService.findByCode(codeCat);
    }

    @Override
    public List<CategoryDto> findAll() {
        return catService.findAll();
    }

    @Override
    public void delete(Long id) {
    catService.delete(id);
    }
}
