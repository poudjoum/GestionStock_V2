package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * Remplir le repertoire — clients ou fournisseurs — depuis un classeur.
 *
 * Le pendant de l'import du catalogue, et pour la meme raison : un commerce qui arrive avec deux
 * cents clients a credit dans un cahier ne les saisira pas un par un. Meme parti pris aussi : on
 * montre avant d'ecrire, et la simulation passe par le meme code que l'ecriture.
 *
 * Une fiche est reconnue par son numero de telephone : c'est ce qu'on connait d'un client au
 * Cameroun, bien avant son courriel. Un fichier corrige et rejoue met donc a jour au lieu de creer
 * des doublons.
 */
public interface ImportRepertoireService {

    /** Les deux repertoires, qui ne different que par leur nom et leur table. */
    enum Repertoire {
        CLIENTS("clients", "client"),
        FOURNISSEURS("fournisseurs", "fournisseur");

        public final String pluriel;
        public final String singulier;

        Repertoire(String pluriel, String singulier) {
            this.pluriel = pluriel;
            this.singulier = singulier;
        }
    }

    RapportImportDto importer(Repertoire repertoire, MultipartFile fichier, boolean simulation);

    /** Le classeur vierge, avec ses colonnes et deux lignes d'exemple : un particulier, une entreprise. */
    byte[] modele(Repertoire repertoire);
}
