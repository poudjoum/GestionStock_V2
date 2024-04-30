package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class CommandeClientValidator {
    public static List<String> validate(CommandeClientDto dto){
        List<String> errors=new ArrayList<>();

        if(dto==null){
            errors.add("Veuillez renseigner le code");
            errors.add("Veuillez selectionner client");
            errors.add("Veuillez renseigner une date");
            return  errors;
        }
        if(!StringUtils.hasLength(dto.getCode())){
            errors.add("Veuillez renseigner le code");
        }

        return errors;
    }
}
