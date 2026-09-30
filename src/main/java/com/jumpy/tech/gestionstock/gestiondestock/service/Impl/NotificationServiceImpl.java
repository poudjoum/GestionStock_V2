package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AbonnementPushDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.NotificationDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.AbonnementPush;
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
import com.jumpy.tech.gestionstock.gestiondestock.repository.AbonnementPushRepository;
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
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final EnvoiRepository envoiRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final Cloisonnement cloisonnement;
    private final AbonnementPushRepository abonnements;
    private final ServicePush push;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   EnvoiRepository envoiRepository,
                                   UtilisateurRepository utilisateurRepository,
                                   Cloisonnement cloisonnement,
                                   AbonnementPushRepository abonnements,
                                   ServicePush push) {
        this.abonnements = abonnements;
        this.push = push;
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
            pousserSurSesAppareils(destinataire, idEntreprise, titre, corps, lien, cle);
        }
        log.info("Notification {} ecrite pour {} destinataire(s)", type, destinataires.size());
    }

    /**
     * Un envoi `PUSH` par appareil abonne du destinataire.
     *
     * Mis en file, et non envoye ici : c'est la meme regle que pour les courriels. Le service push
     * de Google en panne ne doit pas faire echouer la vente qui a declenche l'alerte.
     */
    private void pousserSurSesAppareils(Utilisateur destinataire, Long idEntreprise,
                                        String titre, String corps, String lien, String cle) {
        if (!push.configure()) {
            return;
        }
        List<AbonnementPush> appareils = abonnements.findAllByUtilisateurId(destinataire.getId());
        if (appareils.isEmpty()) {
            return;
        }
        String message = push.message(titre, corps, lien, cle);
        for (AbonnementPush appareil : appareils) {
            Envoi envoi = new Envoi();
            envoi.setCanal(CanalEnvoi.PUSH);
            envoi.setDestination(String.valueOf(appareil.getId()));
            envoi.setSujet(titre.length() <= 300 ? titre : titre.substring(0, 300));
            envoi.setCorps(message);
            envoi.setEtat(EtatEnvoi.A_ENVOYER);
            envoi.setTentatives(0);
            envoi.setProchaineTentative(Instant.now());
            envoi.setIdEntreprise(idEntreprise);
            envoiRepository.save(envoi);
        }
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

    @Override
    public Optional<String> clePush() {
        return push.clePublique();
    }

    @Override
    @Transactional
    public void abonnerCetAppareil(AbonnementPushDto demande, String appareil) {
        if (!push.configure()) {
            throw new InvalidEntityException("Les notifications sur l'appareil ne sont pas activées sur ce serveur",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        String adresse = demande == null ? null : demande.getEndpoint();
        if (!StringUtils.hasText(adresse) || !ServicePush.adresseAdmise(adresse)) {
            // Le serveur ira deposer des messages a cette adresse : elle ne peut etre que celle
            // d'un service push de navigateur. Sans ce controle, s'abonner avec une adresse du
            // reseau interne ferait de ce serveur un relais vers lui.
            throw new InvalidEntityException("Adresse d'abonnement refusée : ce n'est pas celle d'un service push de navigateur",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        AbonnementPushDto.Cles cles = demande.getKeys();
        if (cles == null || taille(cles.getP256dh()) != 65 || taille(cles.getAuth()) != 16) {
            throw new InvalidEntityException("Clés d'abonnement invalides",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }

        Utilisateur moi = compteConnecte();
        AbonnementPush abonnement = abonnements.findByAdresse(adresse).orElseGet(AbonnementPush::new);
        abonnement.setUtilisateur(moi);
        abonnement.setIdEntreprise(moi.getEntreprise() == null ? null : moi.getEntreprise().getId());
        abonnement.setAdresse(adresse);
        abonnement.setCleP256dh(cles.getP256dh());
        abonnement.setCleAuth(cles.getAuth());
        abonnement.setAppareil(appareil == null ? null
                : appareil.length() <= 200 ? appareil : appareil.substring(0, 200));
        abonnements.save(abonnement);
    }

    @Override
    @Transactional
    public void desabonnerCetAppareil(String adresse) {
        Long moi = compteConnecte().getId();
        // Celui d'un autre compte n'est pas touche : connaitre l'adresse d'un appareil ne doit pas
        // suffire a couper les alertes de quelqu'un.
        abonnements.findByAdresse(adresse)
                .filter(a -> a.getUtilisateur().getId().equals(moi))
                .ifPresent(abonnements::delete);
    }

    /** Le nombre d'octets d'une valeur base64url, ou -1 si elle n'en est pas une. */
    private static int taille(String base64url) {
        if (!StringUtils.hasText(base64url)) {
            return -1;
        }
        try {
            return Base64.getUrlDecoder().decode(base64url.trim()).length;
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private Long idDuCompteConnecte() {
        return compteConnecte().getId();
    }

    private Utilisateur compteConnecte() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !cloisonnement.estAuthentifie()) {
            throw new InvalidEntityException("Aucun compte connecté",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        return utilisateurRepository.findUtilisateurByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Le compte connecté n'a pas été retrouvé",
                        ErrorCodes.UTILISATEUR_NOT_FOUND));
    }
}
