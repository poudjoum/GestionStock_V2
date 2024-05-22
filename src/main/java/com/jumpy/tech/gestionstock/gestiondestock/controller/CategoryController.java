package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.CategoryControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
public class CategoryController implements CategoryControllerApi {
    private CategoryService catService;

    public CategoryController(CategoryService catS){
         this.catService=catS;
     }


    @Override
    public ResponseEntity<CategoryDto> save(CategoryDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(catService.save(dto));
    }

    @Override
    public ResponseEntity<CategoryDto> findById(Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(catService.findById(id));
    }

    @Override
    public ResponseEntity<CategoryDto> findByCodeCat(String codeArticle) {
        return ResponseEntity.status(HttpStatus.OK).body(catService.findByCode(codeArticle));
    }

    @Override
    public ResponseEntity<List<CategoryDto>> findAll() {
        return ResponseEntity.status(HttpStatus.OK).body(catService.findAll());
    }

    @Override
    public ResponseEntity delete(Long id) {
        catService.delete(id);
        return ResponseEntity.ok().build();
    }
}
