package com.jumpy.tech.gestionstock.gestiondestock.validator;


import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class EntrepriseValidator {

    /**
     * La taille au-dela de laquelle un logo est refuse, en caracteres du data URI.
     *
     * Une image de 384 pixels de large — la largeur du papier de caisse — pese quelques
     * kilo-octets. Deux cent mille caracteres laissent donc une marge confortable tout en fermant
     * la porte a la photo sortie d'un telephone, que la base transporterait a chaque lecture de
     * l'entreprise et que le papier ne saurait de toute facon pas rendre.
     */
    public static final int LOGO_MAX = 200_000;

    private static final BigDecimal CENT = new BigDecimal("100");
    public static List<String> validate(EntrepriseDto dto){
        List<String> errors=new ArrayList<>();
        if(dto==null){
            errors.add("Veuillez renseigner le registre de commerce");
            errors.add("Veuillez renseigner l'adresse de courriel de l'entreprise");
            errors.add("Veuillez renseigner un numéro de téléphone");
            return  errors;
        }

        // Le nom est ce que le ticket imprime en premier : une entreprise sans nom remet au client
        // un papier qui ne dit pas d'ou il vient.
        if(!StringUtils.hasLength(dto.getNom())){
            errors.add("Veuillez renseigner le nom de l'entreprise");
        }
        // Le registre de commerce et le courriel ne sont plus exiges.
        //
        // Ce sont des mentions qui s'impriment sur les tickets, et le commercant les connait ;
        // l'editeur qui lui ouvre un espace, non. Les exiger a l'inscription obligeait a les
        // inventer ou a rappeler le client avant de pouvoir creer son compte. Ils se renseignent
        // dans « Le magasin », qui est leur place.
        if(!StringUtils.hasLength(dto.getTel())){
            errors.add("Veuillez renseigner un numéro de téléphone");
        }
        if(dto.getNiu()!=null && dto.getNiu().length()>30){
            errors.add("Le NIU ne peut pas dépasser 30 caractères");
        }
        // La base porte deja cette borne ; la redire ici rend un message lisible plutot qu'une
        // violation de contrainte remontee telle quelle.
        if(dto.getTauxTva()!=null
                && (dto.getTauxTva().signum()<0 || dto.getTauxTva().compareTo(CENT)>0)){
            errors.add("Le taux de TVA doit être compris entre 0 et 100");
        }
        if(StringUtils.hasLength(dto.getLogo())){
            if(!dto.getLogo().startsWith("data:image/")){
                errors.add("Le logo doit être une image");
            } else if(dto.getLogo().length()>LOGO_MAX){
                errors.add("Le logo est trop lourd : réduisez-le avant de l'envoyer");
            }
        }

        return errors;
    }
}
