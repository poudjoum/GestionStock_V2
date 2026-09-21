package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.NotificationDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Prevenir quelqu'un, et lui permettre de lire ce dont on l'a prevenu.
 *
 * Rien ne prevenait personne : un article tombait en rupture et on l'apprenait au comptoir, une
 * facture s'emettait sans que le client le sache, un compte s'ouvrait sans que son titulaire en
 * soit informe.
 *
 * La regle qui tient tout : **un echec d'envoi ne fait jamais echouer une operation metier**. Ces
 * methodes n'ecrivent qu'une ligne dans la transaction de l'appelant, ce qui ne peut pas rater ;
 * la livraison vient apres, par un autre chemin.
 */
public interface NotificationService {

    /**
     * Previent les comptes d'une entreprise qui portent l'un des roles donnes.
     *
     * Une ligne par destinataire, parce que l'etat « lu » est personnel : une alerte que le
     * magasinier a traitee ne doit pas disparaitre de l'ecran du gerant.
     *
     * `cle` evite les doublons : tant qu'une notification portant la meme cle attend d'etre lue
     * dans cette entreprise, on n'en ecrit pas d'autre. Sans elle, un article sous son seuil
     * produirait une alerte a chaque vente.
     */
    void prevenirLesRoles(Long idEntreprise, List<ERole> roles, TypeNotification type,
                          String titre, String corps, String lien, String cle);

    /** Met un courriel dans la file. Il partira au prochain passage de l'expediteur. */
    void mettreEnFile(String destination, String sujet, String corps, Long idEntreprise);

    /** Mes notifications, les plus recentes d'abord. `nonLuesSeulement` pour la pastille. */
    Page<NotificationDto> mesNotifications(boolean nonLuesSeulement, Pageable pageable);

    /** Combien attendent d'etre lues : ce que le front affiche sur la cloche. */
    long compteNonLues();

    /** Marque une notification lue. Celle d'un autre compte est introuvable, jamais interdite. */
    NotificationDto marquerLue(Long id);

    /** Tout marquer lu d'un coup, et rendre combien l'ont ete. */
    int marquerToutesLues();
}
