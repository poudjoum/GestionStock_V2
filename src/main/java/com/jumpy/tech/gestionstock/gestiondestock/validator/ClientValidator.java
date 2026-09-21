package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ClientValidator {
    public static List<String> validate(ClientDto dto){
      List<String>errors=new ArrayList<>();
      if(dto==null){
          errors.add("Veuillez renseigner le nom du client");
          errors.add("Veuillez renseigner le prénom du client");
          errors.add("Veuillez renseigner l'adresse de courriel du client");
          errors.add("Veuillez renseigner le numéro de téléphone du client");
          return  errors;
      }
      if(!StringUtils.hasLength(dto.getNom())){
          errors.add("Veuillez renseigner le nom du client");
      }
      if(!StringUtils.hasLength(dto.getPrenoms())){
            errors.add("Veuillez renseigner le prénom du client");
      }
      if(!StringUtils.hasLength(dto.getMail())){
            errors.add("Veuillez renseigner l'adresse de courriel du client");
      }
      if(!StringUtils.hasLength(dto.getNumTel())){
            errors.add("Veuillez renseigner le numéro de téléphone du client");
      }
        return errors;
    }
}
