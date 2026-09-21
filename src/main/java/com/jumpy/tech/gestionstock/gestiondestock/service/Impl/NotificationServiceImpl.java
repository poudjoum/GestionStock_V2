package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.NotificationDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CanalEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Notification;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeNotification;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EnvoiRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.NotificationRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final EnvoiRepository envoiRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final Cloisonnement cloisonnement;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   EnvoiRepository envoiRepository,
                                   UtilisateurRepository utilisateurRepository,
                                   Cloisonnement cloisonnement) {
        this.notificationRepository = notificationRepository;
        this.envoiRepository = envoiRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.cloisonnement = cloisonnement;
    }

    /**
     * Ecrit une notification par destinataire concerne.
     *
     * Dans la transaction de l'appelant, volontairement : une facture emise et son courriel
     * doivent tomber ensemble, ou pas du tout. Ce n'est pas contradictoire avec « un echec
     * d'envoi ne fait pas echouer l'operation » — ce qui se fait ici est une insertion, pas une
     * livraison, et une insertion ne depend d'aucun serveur tiers.
     */
    @Override
    @Transactional
    public void prevenirLesRoles(Long idEntreprise, List<ERole> roles, TypeNotification type,
                                 String titre, String corps, String lien, String cle) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        // Une seule question pour toute l'entreprise : un article sous son seuil est un fait
        // unique, et le repeter a chaque vente ferait de la boite aux lettres un bruit qu'on
        // finit par ignorer.
        if (StringUtils.hasText(cle)
                && notificationRepository.existsByIdEntrepriseAndCleAndLuLeIsNull(idEntreprise, cle)) {
            return;
        }

        List<Utilisateur> destinataires =
                utilisateurRepository.findDestinatairesActifs(idEntreprise, roles);
        if (destinataires.isEmpty()) {
            // Pas une anomalie : une entreprise peut n'avoir aucun magasinier. Le dire une fois
            // evite de chercher longtemps pourquoi une alerte ne s'affiche nulle part.
            log.debug("Aucun destinataire pour {} dans l'entreprise {}", type, idEntreprise);
            return;
        }

        for (Utilisateur destinataire : destinataires) {
            Notification notification = new Notification();
            notification.setDestinataire(destinataire);
            notification.setIdEntreprise(idEntreprise);
            notification.setType(type);
            notification.setTitre(titre);
            notification.setCorps(corps);
            notification.setLien(lien);
            notification.setCle(cle);
            notificationRepository.save(notification);
        }
        log.info("Notification {} ecrite pour {} destinataire(s)", type, destinataires.size());
    }

    @Override
    @Transactional
    public void mettreEnFile(String destination, String sujet, String corps, Long idEntreprise) {
        if (!StringUtils.hasText(destination)) {
            // Un client sans adresse ne recoit rien, et ce n'est pas une erreur : la facture est
            // emise, le ticket est imprime. Refuser la facture pour cela serait absurde.
            log.debug("Courriel non mis en file, destinataire sans adresse : {}", sujet);
            return;
        }
        Envoi envoi = new Envoi();
        envoi.setCanal(CanalEnvoi.EMAIL);
        envoi.setDestination(destination);
        envoi.setSujet(sujet);
        envoi.setCorps(corps);
        envoi.setEtat(EtatEnvoi.A_ENVOYER);
        envoi.setTentatives(0);
        // Tout de suite : l'expediteur prendra ce qui attend a son prochain passage.
        envoi.setProchaineTentative(Instant.now());
        envoi.setIdEntreprise(idEntreprise);
        envoiRepository.save(envoi);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> mesNotifications(boolean nonLuesSeulement, Pageable pageable) {
        Long moi = idDuCompteConnecte();
        return (nonLuesSeulement
                ? notificationRepository.findAllByDestinataireIdAndLuLeIsNullOrderByIdDesc(moi, pageable)
                : notificationRepository.findAllByDestinataireIdOrderByIdDesc(moi, pageable))
                .map(NotificationDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public long compteNonLues() {
        return notificationRepository.countByDestinataireIdAndLuLeIsNull(idDuCompteConnecte());
    }

    /**
     * Marquer lue une notification qui n'est pas la sienne ne rend rien : elle est introuvable,
     * jamais interdite. Un 403 confirmerait qu'elle existe.
     */
    @Override
    @Transactional
    public NotificationDto marquerLue(Long id) {
        Long moi = idDuCompteConnecte();
        Notification notification = notificationRepository.findById(id)
                .filter(n -> n.getDestinataire().getId().equals(moi))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune notification avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.UTILISATEUR_NOT_FOUND));
        if (!notification.estLue()) {
            notification.setLuLe(Instant.now());
            notificationRepository.save(notification);
        }
        return NotificationDto.fromEntity(notification);
    }

    @Override
    @Transactional
    public int marquerToutesLues() {
        return notificationRepository.marquerToutesLues(idDuCompteConnecte(), Instant.now());
    }

    private Long idDuCompteConnecte() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !cloisonnement.estAuthentifie()) {
            throw new InvalidEntityException("Aucun compte connecté",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        return utilisateurRepository.findUtilisateurByUsername(authentication.getName())
                .map(Utilisateur::getId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Le compte connecté n'a pas été retrouvé",
                        ErrorCodes.UTILISATEUR_NOT_FOUND));
    }
}
