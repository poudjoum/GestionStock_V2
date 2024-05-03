package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class LigneCommandeFourValidator {
    public static List<String> validate(LigneCmndeFournisseurDto dto){
        List<String> errors=new ArrayList<>();

        if(dto==null){
            errors.add("Veuillez renseigner le code");
            errors.add("Veuillez selectionner fournisseur");
            errors.add("Veuillez renseigner une date");
            return  errors;
        }
        if(!StringUtils.hasLength(dto.getCmndeFour().getCode())){
            errors.add("Veuillez renseigner le code");
        }

        return errors;
    }
}
