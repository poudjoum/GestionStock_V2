package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ClientValidator {
    public List<String> validate(ClientDto dto){
      List<String>errors=new ArrayList<>();
      if(dto==null){
          errors.add("Veuillez rensigner le nom du client");
          errors.add("Veuillez rensigner le Prenom du client");
          errors.add("Veuillez rensigner le mail du client");
          errors.add("Veuillez rensigner le numero de telephone  du client");
          return  errors;
      }
      if(!StringUtils.hasLength(dto.getNom())){
          errors.add("Veuillez rensigner le nom du client");
      }
      if(!StringUtils.hasLength(dto.getPrenoms())){
            errors.add("Veuillez rensigner le Prenom du client");
      }
      if(!StringUtils.hasLength(dto.getMail())){
            errors.add("Veuillez rensigner le mail du client");
      }
      if(!StringUtils.hasLength(dto.getNumTel())){
            errors.add("Veuillez rensigner le numero de telephone  du client");
      }
        return errors;
    }
}
