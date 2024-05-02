package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class FournisseurValidator {
    public static List<String> validate(FournisseurDto dto){
        List<String>errors=new ArrayList<>();
        if(dto==null){
            errors.add("Veuillez renseigner le nom du fournisseur");
            errors.add("Veuillez renseigner le Prenom du fournisseur");
            errors.add("Veuillez renseigner le mail du fournisseur");
            errors.add("Veuillez renseigner le numero de telephone  du fournisseur");
            return  errors;
        }
        if(!StringUtils.hasLength(dto.getNom())){
            errors.add("Veuillez renseigner le nom du client");
        }
        if(!StringUtils.hasLength(dto.getPrenom())){
            errors.add("Veuillez renseigner le Prenom du fournisseur");
        }
        if(!StringUtils.hasLength(dto.getMail())){
            errors.add("Veuillez renseigner le mail du fournisseur");
        }
        if(!StringUtils.hasLength(dto.getTel())){
            errors.add("Veuillez renseigner le numero de telephone  du fournisseur");
        }
        return errors;
    }
}
