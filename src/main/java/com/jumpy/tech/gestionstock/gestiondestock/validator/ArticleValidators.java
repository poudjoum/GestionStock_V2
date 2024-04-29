package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ArticleValidators {
    public static List<String> validate(ArticleDto dto){
        List<String> errors=new ArrayList<>();
        if(dto==null){
            errors.add("Veuillez renseigner le code de l'article");
            errors.add("Veuillez renseigner le prix de l'article");
            errors.add("Veuillez renseigner la designation");
            errors.add("Veuillez renseigner le prix de l'article");
            errors.add("Veuillez renseigner le taux de la TVA de l'article");
            errors.add("Veuillez renseigner le code de la category de l'article");
            return  errors;
        }

        if(!StringUtils.hasLength(dto.getCodeArticle())){
            errors.add("Veuillez renseigner le code de l'article");
        }
        if(dto.getPrixUnitaireHt()==null){
            errors.add("Veuillez renseigner le prix de l'article");
        }
        if(!StringUtils.hasLength(dto.getDesignation())){
            errors.add("Veuillez renseigner la designation");
        }
        if(dto.getTauxTva()==null){
            errors.add("Veuillez renseigner le taux de la TVA de l'article");
        }
        if(dto.getCategory()==null){
            errors.add("Veuillez renseigner le code de la category de l'article");
        }
        return errors;
    }
}
