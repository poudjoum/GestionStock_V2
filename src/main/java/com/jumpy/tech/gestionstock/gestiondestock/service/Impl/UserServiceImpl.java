package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.ServiceDeRafraichissement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.NouveauCollaborateurDto;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    /**
     * L'equipe du gerant : les metiers du magasin. Il les donne et les retire, et il ne touche
     * qu'aux comptes qui n'ont rien d'autre.
     *
     * Ni administrateur ni gerant : un gerant qui nommerait un autre gerant, ou un administrateur,
     * se donnerait par la un pouvoir que le proprietaire ne lui a pas confie.
     */
    private static final Set<ERole> EQUIPE_DU_GERANT =
            EnumSet.of(ERole.ROLE_CAISSIER, ERole.ROLE_MAGASINIER, ERole.ROLE_COMPTABLE);

    /** Ce que l'administrateur d'un magasin distribue : tout, sauf le rang de l'editeur. */
    private static final Set<ERole> EQUIPE_DE_L_ADMINISTRATEUR = EnumSet.of(
            ERole.ROLE_ADMIN, ERole.ROLE_MANAGER,
            ERole.ROLE_CAISSIER, ERole.ROLE_MAGASINIER, ERole.ROLE_COMPTABLE, ERole.ROLE_USER);

    private final UtilisateurRepository userRepository;
    private final RoleRepository roleRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final PasswordEncoder encodeur;
    private final Cloisonnement cloisonnement;
    private final ServiceDeRafraichissement rafraichissement;
    private final NotificationService notifications;
    private final com.jumpy.tech.gestionstock.gestiondestock.repository.SiteRepository siteRepository;

    public UserServiceImpl(UtilisateurRepository userRepository, RoleRepository roleRepository,
                           EntrepriseRepository entrepriseRepository, PasswordEncoder encodeur,
                           Cloisonnement cloisonnement, ServiceDeRafraichissement rafraichissement,
                           NotificationService notifications,
                           com.jumpy.tech.gestionstock.gestiondestock.repository.SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
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
    @Transactional
    public UserDto ajouterCollaborateur(NouveauCollaborateurDto collaborateur) {
        List<String> erreurs = new ArrayList<>();
        if (collaborateur == null) {
            throw new InvalidEntityException("Le collaborateur n'est pas valide",
                    ErrorCodes.UTILISATEUR_NOT_VALID, List.of("Veuillez renseigner le collaborateur"));
        }
        if (!StringUtils.hasText(collaborateur.getNom())) {
            erreurs.add("Veuillez renseigner le nom du collaborateur");
        }
        if (!StringUtils.hasText(collaborateur.getUsername())) {
            erreurs.add("Veuillez choisir un identifiant de connexion");
        }
        if (!StringUtils.hasText(collaborateur.getNumTel())) {
            erreurs.add("Veuillez renseigner le numéro de téléphone");
        }
        if (!StringUtils.hasLength(collaborateur.getMotDePasse()) || collaborateur.getMotDePasse().length() < 8) {
            erreurs.add("Le mot de passe provisoire fait au moins 8 caractères");
        }
        if (collaborateur.getRoles() == null || collaborateur.getRoles().isEmpty()) {
            erreurs.add("Veuillez choisir au moins un rôle");
        }
        if (!erreurs.isEmpty()) {
            throw new InvalidEntityException("Le collaborateur n'est pas valide",
                    ErrorCodes.UTILISATEUR_NOT_VALID, erreurs);
        }
        verifierRolesAttribuables(collaborateur.getRoles());

        Long idEntreprise = cloisonnement.entrepriseCourante();
        if (idEntreprise == null) {
            // L'editeur n'a pas de magasin : il ouvre les comptes des gerants a l'inscription d'un
            // commerce, pas d'ici.
            throw new InvalidEntityException("Un collaborateur s'ajoute depuis le compte d'un magasin",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }

        String identifiant = collaborateur.getUsername().trim();
        String courriel = StringUtils.hasText(collaborateur.getEmail()) ? collaborateur.getEmail().trim() : null;
        // Ces controles doublent les contraintes d'unicite de la base : ils rendent un message
        // lisible la ou la contrainte, seule, rendrait un conflit sans explication.
        if (Boolean.TRUE.equals(userRepository.existsByUsername(identifiant))) {
            throw new InvalidEntityException("Cet identifiant est déjà pris : choisissez-en un autre",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        if (courriel != null && Boolean.TRUE.equals(userRepository.existsByEmail(courriel))) {
            throw new InvalidEntityException("Cette adresse de courriel est déjà utilisée",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }

        Utilisateur compte = new Utilisateur();
        compte.setNom(collaborateur.getNom().trim());
        compte.setPrenoms(StringUtils.hasText(collaborateur.getPrenoms()) ? collaborateur.getPrenoms().trim() : null);
        compte.setUsername(identifiant);
        compte.setEmail(courriel);
        compte.setNumTel(collaborateur.getNumTel().trim());
        compte.setEntreprise(entreprise(idEntreprise));
        compte.setActif(true);
        compte.setMotdepasse(encodeur.encode(collaborateur.getMotDePasse()));
        // Provisoire : le gerant l'a choisi et le donne de vive voix. Le collaborateur en choisira
        // un autre avant d'entrer, et le gerant cessera de connaitre son mot de passe.
        compte.setMotdepasseAChanger(true);
        compte.setRoles(roles(collaborateur.getRoles()));

        Utilisateur enregistre = userRepository.save(compte);
        annoncerLOuvertureDuCompte(enregistre);
        log.info("Collaborateur {} ajoute a l'entreprise {}", identifiant, idEntreprise);
        return UserDto.fromEntity(enregistre);
    }

    @Override
    public List<ERole> rolesAttribuables() {
        if (cloisonnement.estSuperAdmin() || cloisonnement.aLeRole(ERole.ROLE_ADMIN)) {
            return List.copyOf(EQUIPE_DE_L_ADMINISTRATEUR);
        }
        if (cloisonnement.aLeRole(ERole.ROLE_MANAGER)) {
            return List.copyOf(EQUIPE_DU_GERANT);
        }
        return List.of();
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
        verifierPouvoirSur(utilisateur);
        verifierRolesAttribuables(roles);
        utilisateur.setRoles(vises);
        return UserDto.fromEntity(userRepository.save(utilisateur));
    }

    /**
     * Les sites d'un collaborateur. Ils sont de son entreprise, et le site ou il arrive est l'un
     * d'eux : arriver chaque matin sur un site qui lui est ferme ne lui servirait a rien.
     */
    @Override
    @Transactional
    public UserDto changerSites(Long id, com.jumpy.tech.gestionstock.gestiondestock.dto.SitesDuCompteDto demande) {
        Utilisateur utilisateur = utilisateur(id);
        verifierPouvoirSur(utilisateur);
        Long entreprise = utilisateur.getEntreprise() == null ? null : utilisateur.getEntreprise().getId();
        List<Long> ids = demande == null || demande.idsSites() == null ? List.of() : demande.idsSites();
        Set<com.jumpy.tech.gestionstock.gestiondestock.entities.Site> sites = new HashSet<>();
        for (Long idSite : ids) {
            sites.add(siteRepository.findById(idSite)
                    .filter(s -> java.util.Objects.equals(s.getIdEntreprise(), entreprise) && s.isActif())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Aucun site avec l'identifiant " + idSite + " n'a été trouvé", ErrorCodes.SITE_NOT_FOUND)));
        }
        Long idDefaut = demande == null ? null : demande.idSiteDefaut();
        com.jumpy.tech.gestionstock.gestiondestock.entities.Site parDefaut = null;
        if (idDefaut != null) {
            parDefaut = sites.stream().filter(s -> s.getId().equals(idDefaut)).findFirst()
                    .orElseThrow(() -> new InvalidEntityException(
                            "Le site d'arrivée doit être l'un de ses sites", ErrorCodes.SITE_NOT_VALID));
        }
        utilisateur.setSites(sites);
        utilisateur.setSiteDefaut(parDefaut);
        return UserDto.fromEntity(userRepository.save(utilisateur));
    }

    @Override
    @Transactional
    public UserDto changerActivation(Long id, boolean actif) {
        Utilisateur utilisateur = utilisateur(id);
        verifierPouvoirSur(utilisateur);
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
        verifierPouvoirSur(utilisateur);
        utilisateur.setMotdepasse(encodeur.encode(motDePasseValide(nouveauMotDePasse)));
        // Celui qui reinitialise connait le nouveau mot de passe : il redevient provisoire, et son
        // titulaire en choisira un a lui a la connexion suivante.
        if (!estMoi(utilisateur)) {
            utilisateur.setMotdepasseAChanger(true);
        }
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
        // Le provisoire a servi. C'est ici, et nulle part ailleurs, que le drapeau tombe : le
        // gerant a choisi un mot de passe que l'editeur ne connait pas.
        utilisateur.setMotdepasseAChanger(false);
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

    /**
     * Le gerant n'agit que sur son equipe : un compte dont tous les roles sont des metiers du
     * magasin. Le compte de l'administrateur, ou celui d'un autre gerant, n'est pas a lui.
     *
     * L'administrateur et l'editeur n'ont pas cette limite ; le cloisonnement par entreprise,
     * lui, vaut pour tous et a deja ete verifie en lisant le compte.
     */
    private void verifierPouvoirSur(Utilisateur cible) {
        // Hors de toute authentification — un traitement interne, un test — il n'y a personne a
        // limiter : c'est la regle du cloisonnement, et toutes les routes HTTP exigent un compte.
        if (!cloisonnement.estAuthentifie()
                || cloisonnement.estSuperAdmin() || cloisonnement.aLeRole(ERole.ROLE_ADMIN)) {
            return;
        }
        boolean equipe = cible.getRoles() != null && cible.getRoles().stream()
                .map(Role::getRoleName)
                .allMatch(role -> EQUIPE_DU_GERANT.contains(role) || role == ERole.ROLE_USER);
        if (!equipe) {
            throw new InvalidEntityException("Ce compte n'est pas celui d'un collaborateur : "
                    + "seul l'administrateur du magasin peut le modifier",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
    }

    private void verifierRolesAttribuables(List<ERole> roles) {
        if (!cloisonnement.estAuthentifie()) {
            return;
        }
        List<ERole> permis = rolesAttribuables();
        for (ERole role : roles) {
            if (role == ERole.ROLE_SUPER_ADMIN && cloisonnement.estSuperAdmin()) {
                continue;
            }
            if (!permis.contains(role)) {
                throw new InvalidEntityException("Vous ne pouvez pas donner le rôle " + libelle(role),
                        ErrorCodes.ROLES_NOT_VALIDE);
            }
        }
    }

    private static String libelle(ERole role) {
        return switch (role) {
            case ROLE_SUPER_ADMIN -> "super-administrateur";
            case ROLE_ADMIN -> "administrateur";
            case ROLE_MANAGER -> "gérant";
            case ROLE_CAISSIER -> "caissier";
            case ROLE_MAGASINIER -> "magasinier";
            case ROLE_COMPTABLE -> "comptable";
            case ROLE_USER -> "utilisateur";
        };
    }

    private Set<Role> roles(List<ERole> roles) {
        Set<Role> vises = new HashSet<>();
        for (ERole role : roles) {
            vises.add(roleRepository.findByRoleName(role)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Le rôle " + role + " n'existe pas en base",
                            ErrorCodes.ROLES_NOT_FOUND)));
        }
        return vises;
    }

    private boolean estMoi(Utilisateur utilisateur) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && utilisateur.getUsername() != null
                && utilisateur.getUsername().equals(authentication.getName());
    }
}
