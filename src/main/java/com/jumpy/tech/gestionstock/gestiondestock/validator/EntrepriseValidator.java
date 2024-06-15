package com.jumpy.tech.gestionstock.gestiondestock.validator;


import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class EntrepriseValidator {
    public static List<String> validate(EntrepriseDto dto){
        List<String> errors=new ArrayList<>();
        if(dto==null){
            errors.add("Veuillez renseigner le registre  de commerce");
            errors.add("Veuillez renseigner l' email de l'entreprise");
            errors.add("Veuillez renseigner un numero de telephone ");
            return  errors;
        }

        if(!StringUtils.hasLength(dto.getRegistreCommerce())){
            errors.add("Veuillez renseigner le registre  de commerce");
        }
        if(!StringUtils.hasLength(dto.getEmail())){
            errors.add("Veuillez renseigner l' email de l'entreprise");
        }
        if(!StringUtils.hasLength(dto.getTel())){
            errors.add("Veuillez renseigner un numero de telephone ");
        }

        return errors;
    }
}
