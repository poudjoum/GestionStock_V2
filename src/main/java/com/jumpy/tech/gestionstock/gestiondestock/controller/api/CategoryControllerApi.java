package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface CategoryControllerApi {
    @PostMapping(value = APP_ROOT+"/category/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    CategoryDto save(@RequestBody ArticleDto dto);
    @GetMapping(value = APP_ROOT+"/category/{idCat}",produces = MediaType.APPLICATION_JSON_VALUE)
    CategoryDto findById(@PathVariable("idCat") Long id);
    @GetMapping(value = APP_ROOT+"/category/{codeCat}",produces = MediaType.APPLICATION_JSON_VALUE)
    CategoryDto findByCodeCat(@PathVariable("codeCat") String codeArticle);
    @GetMapping(value = APP_ROOT+"/category/all",produces = MediaType.APPLICATION_JSON_VALUE)
    List<CategoryDto> findAll();
    @DeleteMapping(value = APP_ROOT+"/category/delete/{idArticle}")
    void delete(@PathVariable("idArticle") Long id);
}