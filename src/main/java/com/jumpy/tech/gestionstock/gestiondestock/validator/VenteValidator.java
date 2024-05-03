package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class VenteValidator {
    public static List<String> validate(VenteDto dto){
        List<String> errors=new ArrayList<>();
        if(dto==null){
            errors.add("Veuillez renseigner le code vente");

            return  errors;
        }

        if(!StringUtils.hasLength(dto.getCode())){
            errors.add("Veuillez renseigner le code vente");
        }

        return errors;
    }
}
