package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

import java.util.List;

public interface ArticleControllerApi {
    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/articles/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ArticleDto> save(@RequestBody ArticleDto dto);
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/articles/{idArticle}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ArticleDto> findById(@PathVariable("idArticle") Long id);
    @GetMapping(value = APP_ROOT+"/articles/{codeArticle}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ArticleDto> findByCodeArticle(@PathVariable("codeArticle") String codeArticle);
    @GetMapping(value = APP_ROOT+"/articles/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<ArticleDto>> findAll();
    @DeleteMapping(value = APP_ROOT+"/article/delete/{idArticle}")
   ResponseEntity delete(@PathVariable("idArticle") Long id);

}
