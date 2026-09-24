package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.AdresseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.InscriptionEntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CanalEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EnvoiRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.RoleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.EntrepriseService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.EntrepriseValidator;
import com.jumpy.tech.gestionstock.gestiondestock.validator.UserValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
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
    private final EnvoiRepository envoiRepository;
    /** L'adresse a laquelle le gerant ouvrira l'application. Vide, le courriel n'en parle pas. */
    private final String adressePublique;

    public EntrepriseServiceImpl(EntrepriseRepository entrepriseRepository,
                                 UtilisateurRepository utilisateurRepository,
                                 RoleRepository roleRepository,
                                 PasswordEncoder encodeur,
                                 Cloisonnement cloisonnement,
                                 EnvoiRepository envoiRepository,
                                 @Value("${app.adressePublique:}") String adressePublique){
        this.envoiRepository=envoiRepository;
        this.adressePublique=adressePublique;
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
        errors.addAll(UserValidator.validerPourInscription(inscription.getAdministrateur()));
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

        Entreprise aInscrire = EntrepriseDto.toEntity(inscription.getEntreprise());
        // Un an a compter d'aujourd'hui, sauf echeance donnee. C'est la duree de l'abonnement, et
        // la poser ici evite qu'un commerce inscrit reste sans date — donc, la regle d'acces
        // laissant passer les echeances nulles, sans abonnement du tout.
        if (aInscrire.getAbonnementEcheance() == null) {
            aInscrire.setAbonnementEcheance(LocalDate.now().plusYears(1));
        }
        Entreprise entreprise = entrepriseRepository.save(aInscrire);

        Utilisateur compte = UserDto.toEntity(administrateur);
        compte.setId(null);
        compte.setEntreprise(entreprise);
        compte.setActif(true);
        compte.setMotdepasse(encodeur.encode(administrateur.getMotdepasse()));
        // Provisoire : l'editeur l'a choisi et va l'envoyer par courriel. Le gerant en choisira un
        // autre avant d'entrer, et l'editeur cessera alors de connaitre le mot de passe de son
        // client — ce qui les protege tous les deux.
        compte.setMotdepasseAChanger(true);
        compte.setRoles(new HashSet<>(Set.of(roleRepository.findByRoleName(ERole.ROLE_ADMIN)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Le rôle ROLE_ADMIN n'existe pas en base", ErrorCodes.ROLES_NOT_FOUND)))));
        utilisateurRepository.save(compte);

        annoncerAuGerant(entreprise, compte, administrateur.getMotdepasse());

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

    /**
     * L'entreprise du compte connecte, deduite de son jeton.
     *
     * Un compte sans entreprise — le super-administrateur, ou un compte anterieur au
     * cloisonnement — n'en a pas a rendre, et c'est un 404 : il n'y a rien a montrer, pas un
     * droit qui manque. Le front imprime alors ses documents sans en-tete plutot que de refuser
     * d'imprimer.
     */
    @Override
    public EntrepriseDto mienne() {
        Long id = cloisonnement.entrepriseCourante();
        if (id == null) {
            throw new EntityNotFoundException(
                    "Ce compte n'est rattaché à aucune entreprise",
                    ErrorCodes.ENTREPRISE_NOT_FOUND);
        }
        return findById(id);
    }

    @Override
    @Transactional
    public EntrepriseDto mettreAJourMienne(EntrepriseDto dto) {
        Long id = cloisonnement.entrepriseCourante();
        if (id == null) {
            throw new EntityNotFoundException(
                    "Ce compte n'est rattaché à aucune entreprise",
                    ErrorCodes.ENTREPRISE_NOT_FOUND);
        }
        List<String> errors = EntrepriseValidator.validate(dto);
        if (!errors.isEmpty()) {
            log.error("Entreprise Invalid {}", errors);
            throw new InvalidEntityException("L'entreprise n'est pas valide",
                    ErrorCodes.ENTREPRISE_NOT_VALID, errors);
        }
        Entreprise entreprise = entrepriseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune entreprise avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.ENTREPRISE_NOT_FOUND));

        // Champ par champ, et non `toEntity` : celui-ci construit une entreprise neuve, dont
        // l'identifiant viendrait du corps de la requete. Ecrire les champs sur l'entite chargee
        // garantit que c'est bien l'entreprise du jeton qui est modifiee, et qu'aucune colonne
        // absente du formulaire — les comptes rattaches — n'est effacee au passage.
        entreprise.setNom(dto.getNom());
        entreprise.setDescription(dto.getDescription());
        entreprise.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
        entreprise.setRegistreCommerce(dto.getRegistreCommerce());
        entreprise.setNiu(dto.getNiu());
        entreprise.setEmail_Entreprise(dto.getEmail());
        entreprise.setTel(dto.getTel());
        entreprise.setSiteWeb(dto.getSiteWeb());
        entreprise.setLogo(dto.getLogo());
        // Une entreprise est assujettie sauf mention contraire, comme a la creation : une omission
        // ne doit pas la faire passer pour exoneree.
        entreprise.setAssujettieTva(dto.getAssujettieTva() == null || dto.getAssujettieTva());
        entreprise.setTauxTva(dto.getTauxTva() == null
                ? EntrepriseDto.TAUX_TVA_PAR_DEFAUT
                : dto.getTauxTva());

        return EntrepriseDto.fromEntity(entrepriseRepository.save(entreprise));
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

    /**
     * Met en file le courriel qui porte au gerant de quoi entrer.
     *
     * Dans la transaction de l'inscription : le compte et son annonce tombent ensemble, ou pas du
     * tout. La livraison, elle, viendra plus tard et ailleurs — un serveur SMTP en panne ne doit
     * pas faire echouer l'inscription d'un client.
     *
     * Le message porte un mot de passe en clair : il est marque sensible, et la file effacera son
     * corps une fois parti. C'est ce que rend acceptable le caractere provisoire de ce mot de
     * passe, que le gerant devra changer avant toute autre chose.
     */
    private void annoncerAuGerant(Entreprise entreprise, Utilisateur compte, String motdepasse) {
        if (!StringUtils.hasText(compte.getEmail())) {
            return;
        }
        // Un bloc de texte plutot qu'une suite de concatenations : le courriel se relit tel
        // qu'il sera lu, et c'est le seul endroit du projet ou la mise en page compte pour
        // quelqu'un qui n'a pas l'application sous les yeux.
        String corps = """
                Bonjour,

                Votre espace %s est ouvert sur Gestion de Stock.

                Identifiant : %s
                Mot de passe provisoire : %s

                Ce mot de passe ne sert qu'une fois : l'application vous demandera d'en choisir
                un autre dès votre première connexion.
                """.formatted(entreprise.getNom(), compte.getUsername(), motdepasse);
        if (StringUtils.hasText(adressePublique)) {
            corps = corps + System.lineSeparator() + "Adresse : " + adressePublique;
        }
        if (entreprise.getAbonnementEcheance() != null) {
            corps = corps + System.lineSeparator()
                    + "Votre abonnement court jusqu'au " + entreprise.getAbonnementEcheance() + ".";
        }

        Envoi envoi = new Envoi();
        envoi.setCanal(CanalEnvoi.EMAIL);
        envoi.setDestination(compte.getEmail());
        envoi.setSujet("Vos accès à Gestion de Stock — " + entreprise.getNom());
        envoi.setCorps(corps);
        envoi.setEtat(EtatEnvoi.A_ENVOYER);
        envoi.setTentatives(0);
        envoi.setProchaineTentative(Instant.now());
        envoi.setIdEntreprise(entreprise.getId());
        envoi.setSensible(true);
        envoiRepository.save(envoi);
    }
}
