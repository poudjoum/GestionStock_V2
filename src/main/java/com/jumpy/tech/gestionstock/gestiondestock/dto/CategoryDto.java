package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Category;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class CategoryDto {

    private Long Id;
    private String codeCategorie;

    private String designation;
    @JsonIgnore
    private List<ArticleDto> articles;

    public static CategoryDto fromEntity(Category category) {
        if(category==null) {
            return null;
        }
        return CategoryDto.builder()
                .Id(category.getId())
                .codeCategorie(category.getCodeCat())
                .designation(category.getDesignation())
                .build();

    }
    public static Category toEntity(CategoryDto categoryDto) {
        if (categoryDto == null) {
            return null;
        }
        Category category = new Category();
        category.setId(categoryDto.getId());
        category.setCodeCat(categoryDto.getCodeCategorie());
        category.setDesignation(categoryDto.getDesignation());

        return category;
    }

}
