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
            errors.add("Veuillez renseigner le code de la commande");
            errors.add("Veuillez sélectionner le fournisseur");
            errors.add("Veuillez renseigner une date");
            return  errors;
        }
        // Meme piege que pour la ligne de commande client : getCmndeFour() peut etre null.
        if(dto.getCmndeFour() == null || !StringUtils.hasLength(dto.getCmndeFour().getCode())){
            errors.add("Veuillez renseigner le code de la commande");
        }

        return errors;
    }
}
