package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ArticleValidators {
    public static List<String> validate(ArticleDto dto){
        List<String> errors=new ArrayList<>();
        if(dto==null){
            errors.add("Veuillez renseigner le code de l'article");
            errors.add("Veuillez renseigner le prix de l'article");
            errors.add("Veuillez renseigner la désignation");
            errors.add("Veuillez renseigner la catégorie de l'article");
            return  errors;
        }

        if(!StringUtils.hasLength(dto.getCodeArticle())){
            errors.add("Veuillez renseigner le code de l'article");
        }
        if(dto.getPrixUnitaireHt()==null){
            errors.add("Veuillez renseigner le prix de l'article");
        }
        if(!StringUtils.hasLength(dto.getDesignation())){
            errors.add("Veuillez renseigner la désignation");
        }
        // Le taux de TVA n'est plus exige : il se parametre sur l'entreprise, et ne se porte sur
        // un article que pour en faire une exception — produit exonere ou a taux reduit.
        if(dto.getTauxTva()!=null
                && (dto.getTauxTva().signum() < 0 || dto.getTauxTva().compareTo(new BigDecimal("100")) > 0)){
            errors.add("Le taux de TVA doit être compris entre 0 et 100");
        }
        if(dto.getCategory()==null){
            errors.add("Veuillez renseigner la catégorie de l'article");
        }
        return errors;
    }
}
