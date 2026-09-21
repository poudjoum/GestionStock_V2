package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.InscriptionEntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.RoleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.EntrepriseService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.EntrepriseValidator;
import com.jumpy.tech.gestionstock.gestiondestock.validator.UserValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
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
public class EntrepriseServiceImpl implements EntrepriseService {

    private EntrepriseRepository entrepriseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encodeur;
    private final Cloisonnement cloisonnement;

    public EntrepriseServiceImpl(EntrepriseRepository entrepriseRepository,
                                 UtilisateurRepository utilisateurRepository,
                                 RoleRepository roleRepository,
                                 PasswordEncoder encodeur,
                                 Cloisonnement cloisonnement){
        this.entrepriseRepository=entrepriseRepository;
        this.utilisateurRepository=utilisateurRepository;
        this.roleRepository=roleRepository;
        this.encodeur=encodeur;
        this.cloisonnement=cloisonnement;
    }

    /**
     * Cree l'entreprise et son premier administrateur dans la meme transaction.
     *
     * Ouvert a deux situations seulement : le super-administrateur, qui distribue les entreprises,
     * et une installation qui n'en compte encore aucune — il faut bien creer la premiere, et
     * personne ne peut alors l'autoriser. C'est la meme regle que pour le tout premier compte.
     */
    @Override
    @Transactional
    public EntrepriseDto inscrire(InscriptionEntrepriseDto inscription) {
        if (inscription == null || inscription.getEntreprise() == null || inscription.getAdministrateur() == null) {
            throw new InvalidEntityException("Une inscription porte une entreprise et son administrateur",
                    ErrorCodes.ENTREPRISE_NOT_VALID);
        }
        boolean premiereInstallation = entrepriseRepository.count() == 0;
        if (!premiereInstallation && !cloisonnement.estSuperAdmin()) {
            throw new AccessDeniedException("Seul un super-administrateur inscrit une entreprise");
        }

        List<String> errors = EntrepriseValidator.validate(inscription.getEntreprise());
        errors.addAll(UserValidator.validate(inscription.getAdministrateur()));
        UserDto administrateur = inscription.getAdministrateur();
        if (!StringUtils.hasLength(administrateur.getUsername())) {
            errors.add("Veuillez renseigner l'identifiant de connexion de l'administrateur");
        }
        if (!StringUtils.hasLength(administrateur.getEmail())) {
            errors.add("Veuillez renseigner l'adresse de courriel de l'administrateur");
        }
        if (!errors.isEmpty()) {
            throw new InvalidEntityException("L'inscription n'est pas valide",
                    ErrorCodes.ENTREPRISE_NOT_VALID, errors);
        }
        // Ces deux controles doublent les contraintes d'unicite de la base : ils rendent un
        // message lisible la ou la contrainte, seule, rendrait un conflit sans explication.
        if (Boolean.TRUE.equals(utilisateurRepository.existsByUsername(administrateur.getUsername()))) {
            throw new InvalidEntityException("Cet identifiant est déjà pris",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }
        if (Boolean.TRUE.equals(utilisateurRepository.existsByEmail(administrateur.getEmail()))) {
            throw new InvalidEntityException("Cette adresse de courriel est déjà utilisée",
                    ErrorCodes.UTILISATEUR_NOT_VALID);
        }

        Entreprise entreprise = entrepriseRepository.save(EntrepriseDto.toEntity(inscription.getEntreprise()));

        Utilisateur compte = UserDto.toEntity(administrateur);
        compte.setId(null);
        compte.setEntreprise(entreprise);
        compte.setActif(true);
        compte.setMotdepasse(encodeur.encode(administrateur.getMotdepasse()));
        compte.setRoles(new HashSet<>(Set.of(roleRepository.findByRoleName(ERole.ROLE_ADMIN)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Le rôle ROLE_ADMIN n'existe pas en base", ErrorCodes.ROLES_NOT_FOUND)))));
        utilisateurRepository.save(compte);

        log.info("Entreprise {} inscrite avec l'administrateur {}", entreprise.getId(), compte.getUsername());
        return EntrepriseDto.fromEntity(entreprise);
    }
    @Override
    @Transactional
    public EntrepriseDto save(EntrepriseDto dto) {
        List<String> errors= EntrepriseValidator.validate(dto);
         if(!errors.isEmpty()){
             log.error("Entreprise Invalid {}",dto);
             throw new InvalidEntityException("L'entreprise n'est pas valide", ErrorCodes.ENTREPRISE_NOT_VALID,errors);
         }
         Entreprise savedEntreprise=entrepriseRepository.save(EntrepriseDto.toEntity(dto));


        return EntrepriseDto.fromEntity(savedEntreprise);
    }

    @Override
    public EntrepriseDto findById(Long id) {
        if(id==null){
            log.error("Entreprise id is null");
            throw new InvalidEntityException("Aucune entreprise ne peut être cherchée sans identifiant",
                    ErrorCodes.ENTREPRISE_NOT_VALID);
        }
        return entrepriseRepository.findById(id)
                .map(EntrepriseDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune entreprise avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.ENTREPRISE_NOT_FOUND));
    }

    @Override
    public List<EntrepriseDto> findAll() {
        return entrepriseRepository.findAll().stream()
                .map(EntrepriseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Entreprise ID is null");
            return;
        }
        entrepriseRepository.deleteById(id);

    }
}
