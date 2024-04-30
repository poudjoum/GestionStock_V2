package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

import java.util.List;

public interface ArticleControllerApi {
    @PostMapping(value = APP_ROOT+"/articles/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ArticleDto save(@RequestBody ArticleDto dto);
    @GetMapping(value = APP_ROOT+"/articles/{idArticle}",produces = MediaType.APPLICATION_JSON_VALUE)
    ArticleDto findById(@PathVariable("idArticle") Long id);
    @GetMapping(value = APP_ROOT+"/articles/{codeArticle}",produces = MediaType.APPLICATION_JSON_VALUE)
    ArticleDto findByCodeArticle(@PathVariable("codeArticle") String codeArticle);
    @GetMapping(value = APP_ROOT+"/articles/all",produces = MediaType.APPLICATION_JSON_VALUE)
    List<ArticleDto> findAll();
    @DeleteMapping(value = APP_ROOT+"/article/delete/{idArticle}")
    void delete(@PathVariable("idArticle") Long id);

}
