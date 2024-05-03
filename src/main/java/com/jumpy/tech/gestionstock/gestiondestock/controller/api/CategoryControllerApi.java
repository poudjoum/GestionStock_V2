package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface CategoryControllerApi {
    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/category/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    CategoryDto save(@RequestBody CategoryDto dto);
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/category/{idCat}",produces = MediaType.APPLICATION_JSON_VALUE)
    CategoryDto findById(@PathVariable("idCat") Long id);
    @GetMapping(value = APP_ROOT+"/category/{codeCat}",produces = MediaType.APPLICATION_JSON_VALUE)
    CategoryDto findByCodeCat(@PathVariable("codeCat") String codeArticle);
    @GetMapping(value = APP_ROOT+"/category/all",produces = MediaType.APPLICATION_JSON_VALUE)
    List<CategoryDto> findAll();
    @DeleteMapping(value = APP_ROOT+"/category/delete/{idArticle}")
    void delete(@PathVariable("idArticle") Long id);
}
