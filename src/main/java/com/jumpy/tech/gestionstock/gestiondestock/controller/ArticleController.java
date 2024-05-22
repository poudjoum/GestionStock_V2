package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.ArticleControllerApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.ArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ArticleController implements ArticleControllerApi {
    private ArticleService articleService;

    public ArticleController(ArticleService articleService){
        this.articleService=articleService;
    }


    @Override
    public ResponseEntity<ArticleDto> save(ArticleDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(articleService.save(dto));
    }

    @Override
    public ResponseEntity<ArticleDto> findById(Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(articleService.findById(id));
    }

    @Override
    public ResponseEntity<ArticleDto> findByCodeArticle(String codeArticle) {
        return ResponseEntity.status(HttpStatus.OK).body(articleService.findByCodeArticle(codeArticle));
    }

    @Override
    public ResponseEntity<List<ArticleDto>> findAll() {
        return ResponseEntity.status(HttpStatus.OK).body(articleService.findAll());
    }

    @Override
    public ResponseEntity delete(Long id) {
        articleService.delete(id);
        return ResponseEntity.ok().build();
    }
}
