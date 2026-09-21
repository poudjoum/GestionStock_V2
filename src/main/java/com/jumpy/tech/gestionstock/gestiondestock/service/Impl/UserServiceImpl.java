package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.ServiceDeRafraichissement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Role;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.RoleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.NotificationService;
import com.jumpy.tech.gestionstock.gestiondestock.service.UserService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.UserValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final UtilisateurRepository userRepository;
    private final RoleRepository roleRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final PasswordEncoder encodeur;
    private final Cloisonnement cloisonnement;
    private final ServiceDeRafraichissement rafraichissement;
    private final NotificationService notifications;

    public UserServiceImpl(UtilisateurRepository userRepository, RoleRepository roleRepository,
                           EntrepriseRepository entrepriseRepository, PasswordEncoder encodeur,
                           Cloisonnement cloisonnement, ServiceDeRafraichissement rafraichissement,
                           NotificationService notifications) {
        this.notifications = notifications;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.encodeur = encodeur;
        this.cloisonnement = cloisonnement;
        this.rafraichissement = rafraichissement;
    }

    @Override
    @Transactional
    public UserDto save(UserDto dto) {
        List<String> errors = UserValidator.validate(dto);
        if (!errors.isEmpty()) {
            log.error("User not Valid");
            throw new InvalidEntityException("L'utilisateur n'est pas valide", ErrorCodes.UTILISATEUR_NOT_VALID, errors);
        }

        Utilisateur utilisateur = UserDto.toEntity(dto);

        // Le compte cree rejoint l'entreprise de celui qui le cree. L'entreprise envoyee dans la
        // requete est ignoree : un administrateur ne cree pas de compte chez le voisin.
        if (cloisonnement.filtre()) {
            utilisateur.setEntreprise(entreprise(cloisonnement.entrepriseCourante()));
        } else if (dto.getEntreprise() != null && dto.getEntreprise().getId() != null) {
            utilisateur.setEntreprise(entreprise(dto.getEntreprise().getId()));
        }

        // Le mot de passe est chiffre ici : il arrivait en clair et repartait tel quel en base,
        // ou la connexion, qui compare a une empreinte BCrypt, ne l'aurait jamais reconnu.
        if (StringUtils.hasLength(dto.getMotdepasse())) {
            utilisateur.setMotdepasse(encodeur.encode(dto.getMotdepasse()));
        }
        boolean creation = utilisateur.getId() == null;
        if (creation) {
            utilisateur.setActif(true);
        }

        Utilisateur enregistre = userRepository.save(utilisateur);
        if (creation) {
            annoncerLOuvertureDuCompte(enregistre);
        }
        return UserDto.fromEntity(enregistre);
    }

    /**
     * Previent le titulaire qu'un compte lui a ete ouvert.
     *
     * Le mot de passe n'y figure pas : il est chiffre avant d'arriver ici, et personne ne le
     * connait plus en clair. C'est a celui qui cree le compte de le transmettre — l'ecrire dans
     * un courriel le laisserait dormir dans une boite aux lettres pour des annees.
     */
    private void annoncerLOuvertureDuCompte(Utilisateur utilisateur) {
        if (!StringUtils.hasText(utilisateur.getEmail())) {
            return;
        }
        String maison = utilisateur.getEntreprise() == null || utilisateur.getEntreprise().getNom() == null
                ? "l'application de gestion de stock" : utilisateur.getEntreprise().getNom();

        notifications.mettreEnFile(
                utilisateur.getEmail(),
                "Votre compte a été créé",
                "Bonjour,\n\nUn compte vient d'être ouvert pour vous sur " + maison + ".\n"
                        + "Votre identifiant de connexion est : " + utilisateur.getUsername() + "\n\n"
                        + "Le mot de passe vous est communiqué séparément par la personne qui a "
                        + "créé ce compte.\n",
                utilisateur.getEntreprise() == null ? null : utilisateur.getEntreprise().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto findById(Long id) {
        if (id == null) {
            log.error("user id is null");
            throw new InvalidEntityException("Aucun utilisateur ne peut être cherché sans identifiant",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        return UserDto.fromEntity(utilisateur(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto findUserByEmail(String email) {
        if (!StringUtils.hasLength(email)) {
            log.error("L'email est vide");
            throw new InvalidEntityException("Aucun utilisateur ne peut être cherché sans adresse de courriel",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        Utilisateur utilisateur = userRepository.findUtilisateurByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun utilisateur avec l'adresse " + email + " n'a été trouvé",
                        ErrorCodes.UTILISATEUR_NOT_FOUND));
        verifierAcces(utilisateur, email);
        return UserDto.fromEntity(utilisateur);
    }

    /**
     * Le compte connecte.
     *
     * Aucun controle de cloisonnement : on ne demande pas a quelqu'un s'il a le droit de savoir
     * qui il est. C'est d'ailleurs la seule route de `/users` ouverte a tout compte connecte.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDto moi() {
        return UserDto.fromEntity(compteConnecte());
    }

    /**
     * `readOnly` n'est pas decoratif : `open-in-view` est desactive, et les roles d'un compte sont
     * charges a la demande. Hors transaction, leur lecture par le DTO partait en
     * LazyInitializationException.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return (cloisonnement.filtre()
                ? userRepository.findAllByEntrepriseId(cloisonnement.entrepriseCourante())
                : userRepository.findAllBy()).stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserDto changerRoles(Long id, List<ERole> roles) {
        Utilisateur utilisateur = utilisateur(id);
        if (roles == null || roles.isEmpty()) {
            throw new InvalidEntityException("Un compte sans rôle ne peut rien faire",
                    ErrorCodes.ROLES_NOT_VALIDE);
        }
        // Seul le super-administrateur distribue son propre rang : un administrateur qui se
        // l'accorderait sortirait de son entreprise par la porte de derriere.
        if (roles.contains(ERole.ROLE_SUPER_ADMIN) && !cloisonnement.estSuperAdmin()) {
            throw new InvalidEntityException("Seul un super-administrateur accorde ce rôle",
                    ErrorCodes.ROLES_NOT_VALIDE);
        }

        Set<Role> vises = new HashSet<>();
        for (ERole role : roles) {
            vises.add(roleRepository.findByRoleName(role)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Le rôle " + role + " n'existe pas en base",
                            ErrorCodes.ROLES_NOT_FOUND)));
        }
        utilisateur.setRoles(vises);
        return UserDto.fromEntity(userRepository.save(utilisateur));
    }

    @Override
    @Transactional
    public UserDto changerActivation(Long id, boolean actif) {
        Utilisateur utilisateur = utilisateur(id);
        if (!actif && estMoi(utilisateur)) {
            // Se fermer soi-meme l'acces laisserait une entreprise sans personne pour rouvrir.
            throw new InvalidEntityException("On ne ferme pas son propre accès",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        utilisateur.setActif(actif);
        // Fermer l'acces sans fermer les jetons ne fermerait que la porte d'entree : le jeton de
        // rafraichissement deja delivre continuerait a fabriquer des jetons d'acces.
        if (!actif) {
            rafraichissement.revoquerTout(id);
        }
        return UserDto.fromEntity(userRepository.save(utilisateur));
    }

    @Override
    @Transactional
    public UserDto rattacherAEntreprise(Long id, Long idEntreprise) {
        if (!cloisonnement.estSuperAdmin()) {
            throw new InvalidEntityException(
                    "Seul un super-administrateur rattache un compte à une entreprise",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        Utilisateur utilisateur = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun utilisateur avec l'identifiant " + id + " n'a été trouvé",
                        ErrorCodes.UTILISATEUR_NOT_FOUND));
        utilisateur.setEntreprise(idEntreprise == null ? null : entreprise(idEntreprise));
        return UserDto.fromEntity(userRepository.save(utilisateur));
    }

    @Override
    @Transactional
    public UserDto reinitialiserMotDePasse(Long id, String nouveauMotDePasse) {
        Utilisateur utilisateur = utilisateur(id);
        utilisateur.setMotdepasse(encodeur.encode(motDePasseValide(nouveauMotDePasse)));
        // Un mot de passe qu'on reinitialise est un mot de passe qu'on soupconne : les sessions
        // ouvertes ailleurs tombent avec lui.
        rafraichissement.revoquerTout(id);
        log.info("Mot de passe reinitialise pour le compte {}", id);
        return UserDto.fromEntity(userRepository.save(utilisateur));
    }

    @Override
    @Transactional
    public UserDto changerSonMotDePasse(String ancien, String nouveau) {
        Utilisateur utilisateur = compteConnecte();
        // L'ancien est exige : sans lui, un poste laisse ouvert une minute suffirait a verrouiller
        // le compte de son titulaire.
        if (!StringUtils.hasLength(ancien) || !encodeur.matches(ancien, utilisateur.getMotdepasse())) {
            throw new InvalidEntityException("L'ancien mot de passe ne correspond pas",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        utilisateur.setMotdepasse(encodeur.encode(motDePasseValide(nouveau)));
        // Changer son mot de passe ferme ses autres sessions : c'est le geste de quelqu'un qui
        // soupconne que son acces a fuite.
        rafraichissement.revoquerTout(utilisateur.getId());
        return UserDto.fromEntity(userRepository.save(utilisateur));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == null) {
            log.error("Utilisateur Id est null");
            return;
        }
        userRepository.delete(utilisateur(id));
    }

    /** Le compte, a condition qu'il soit de l'entreprise de l'appelant. */
    private Utilisateur utilisateur(Long id) {
        Utilisateur utilisateur = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun utilisateur avec l'identifiant " + id + " n'a été trouvé",
                        ErrorCodes.UTILISATEUR_NOT_FOUND));
        verifierAcces(utilisateur, id);
        return utilisateur;
    }

    private void verifierAcces(Utilisateur utilisateur, Object identifiant) {
        Long entrepriseDuCompte = utilisateur.getEntreprise() == null
                ? null : utilisateur.getEntreprise().getId();
        cloisonnement.verifierAcces(entrepriseDuCompte, "utilisateur", identifiant);
    }

    private Entreprise entreprise(Long id) {
        if (id == null) {
            return null;
        }
        return entrepriseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune entreprise avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.ENTREPRISE_NOT_FOUND));
    }

    private String motDePasseValide(String motDePasse) {
        if (!StringUtils.hasLength(motDePasse) || motDePasse.length() < 8) {
            throw new InvalidEntityException("Le mot de passe fait au moins 8 caractères",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        return motDePasse;
    }

    private Utilisateur compteConnecte() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new InvalidEntityException("Aucun compte connecté", ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        return userRepository.findUtilisateurByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Le compte connecté n'a pas été retrouvé",
                        ErrorCodes.UTILISATEUR_NOT_FOUND));
    }

    private boolean estMoi(Utilisateur utilisateur) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && utilisateur.getUsername() != null
                && utilisateur.getUsername().equals(authentication.getName());
    }
}
