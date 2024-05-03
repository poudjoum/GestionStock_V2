package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class CategoryValidator {
    public static List<String> validate(CategoryDto dto){
        List<String> errors=new ArrayList<>();
        if(dto==null || !StringUtils.hasLength(dto.getCodeCategorie())){
            errors.add("Veuillez renseigner le code de la catégorie");
        }
        return errors;
    }

}
