package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * Remplir le catalogue d'un coup, depuis un classeur.
 *
 * Un commerce qui s'abonne arrive avec des centaines d'articles et un catalogue vide. Les saisir
 * un par un est le mur contre lequel bute l'arrivee d'un nouvel abonne : sans articles, il n'y a
 * rien a scanner, rien a vendre, rien a compter.
 */
public interface ImportArticleService {

    /**
     * Lit le classeur et rend ce qui entrerait, ou ce qui est entre.
     *
     * Un seul chemin pour les deux, parce que la simulation ne vaut que si elle dit la verite :
     * deux implementations separees divergeraient, et l'ecart se decouvrirait apres validation.
     *
     * Les articles sont reconnus par leur code, unique dans l'entreprise depuis la V20 : un
     * fichier corrige et rejoue met a jour au lieu de doubler. C'est ce qui rend l'import
     * rejouable, et donc utilisable par quelqu'un qui n'ose pas se tromper.
     *
     * @param simulation vrai pour ne rien ecrire ; le rapport est identique dans les deux cas
     * @throws com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException
     *         si le fichier est absent, vide, illisible, ou si ses colonnes ne sont pas celles du
     *         modele — auquel cas aucune ligne n'est examinee
     */
    RapportImportDto importer(MultipartFile fichier, boolean simulation);

    /**
     * Le classeur vierge a remplir, en-tete et une ligne d'exemple.
     *
     * Les colonnes sont imposees : c'est ce qui permet de refuser proprement un fichier au lieu
     * de deviner ce que le commercant a voulu mettre dans sa troisieme colonne.
     */
    byte[] modele();
}
