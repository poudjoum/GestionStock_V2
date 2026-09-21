package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.*;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CommandeFourRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FournisseurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeFourRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeFourService;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.CommandFourValidator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommandeFourServiceImpl implements CommandeFourService {
    private CommandeFourRepository commandeFourRepository;
    private ArticleRepository articleRepository;
    private LigneCmndeFourRepository ligneCmndeFourRepository;
    private FournisseurRepository fournisseurRepository;
    private final Cloisonnement cloisonnement;
    private MvtStkService mvtStkService;

    public CommandeFourServiceImpl(CommandeFourRepository commandeFourRepository, ArticleRepository articleRepository,
                                   LigneCmndeFourRepository ligneCmndeFourRepository, FournisseurRepository fournisseurRepository,
                                   MvtStkService mvtStkService, Cloisonnement cloisonnement){
        this.cloisonnement=cloisonnement;
        this.commandeFourRepository=commandeFourRepository;
        this.articleRepository=articleRepository;
        this.fournisseurRepository=fournisseurRepository;
        this.ligneCmndeFourRepository=ligneCmndeFourRepository;
        this.mvtStkService=mvtStkService;
    }

    /**
     * Enregistre la commande, ses lignes, et l'entree en stock de chaque ligne.
     *
     * La marchandise n'entre plus en stock ici. Une commande nait EN_PREPARATION, et c'est son
     * passage en LIVREE qui alimente le magasin — le stock ne monte donc plus avant que le camion
     * n'arrive. C'etait l'approximation assumee tant que la commande n'avait pas d'etat.
     */
    @Override
    @Transactional
    public CommandeFourDto save(CommandeFourDto dto) {
        List<String> errors= CommandFourValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Commande Fournisseur n'est pas valide {}",dto);
            throw new InvalidEntityException("La commande fournisseur n'est pas valide", ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID,errors);
        }
        Optional<Fournisseur> fournisseur=fournisseurRepository.findById(dto.getFournisseur().getId());
        if(fournisseur.isEmpty()){
            log.warn("Fournisseur with Id {} was not in database",dto.getFournisseur().getId());
            throw new EntityNotFoundException("Aucun fournisseur avec l'identifiant "+dto.getFournisseur().getId()+" n'a été trouvé",
                    ErrorCodes.FOURNISSEUR_NOT_FOUND);
        }
        List<String> articleErrors=new ArrayList<>();
        if(dto.getLigneCmndeFournisseur()!=null){
            dto.getLigneCmndeFournisseur().forEach(ligCmdClt->{
                if(ligCmdClt.getArticle()!=null){
                    Optional<Article>article=articleRepository.findById(ligCmdClt.getArticle().getId());
                    if(article.isEmpty()){
                        articleErrors.add("L'article avec l'identifiant "+ligCmdClt.getArticle().getId()+" n'existe pas");
                    }
                }else{
                    articleErrors.add("Impossible d'enregistrer une commande sans article");
                }

            });
        }
        if(!articleErrors.isEmpty()){
            log.error("");
            throw new InvalidEntityException("Un ou plusieurs articles de la commande n'existent pas",ErrorCodes.ARTICLE_NOT_FOUND,articleErrors);
        }
        CommandeFour aEnregistrer = CommandeFourDto.toEntity(dto);
        // L'etat ne se choisit pas a la creation : une commande que l'on pourrait declarer LIVREE
        // d'emblee ferait entrer en stock une marchandise que personne n'a vue arriver.
        aEnregistrer.setEtat(EtatCommande.EN_PREPARATION);
        if (cloisonnement.filtre()) {
            aEnregistrer.setIdEntreprise(cloisonnement.entrepriseCourante());
        }
        CommandeFour saveCmndFour=commandeFourRepository.save(aEnregistrer);
        if(dto.getLigneCmndeFournisseur()!=null) {
            dto.getLigneCmndeFournisseur().forEach(ligCmdFour -> {
                LigneCmndeFournisseur ligneCmndeFour = LigneCmndeFournisseurDto.toEntity(ligCmdFour);
                ligneCmndeFour.setCommandeFournisseur(saveCmndFour);
                ligneCmndeFourRepository.save(ligneCmndeFour);
            });
        }
        return CommandeFourDto.fromEntity(saveCmndFour);
    }

    /**
     * Fait avancer la commande dans son cycle de vie.
     *
     * Le passage en LIVREE est le moment ou la marchandise entre en magasin. Il est donc le seul
     * a ecrire des mouvements, et l'etat lui-meme garantit qu'il ne le fera qu'une fois : une
     * commande livree ne peut plus changer d'etat, donc plus rien relire ses lignes.
     */
    @Override
    @Transactional
    public CommandeFourDto mettreAJourEtat(Long id, EtatCommande etat) {
        CommandeFour commande = commande(id);
        verifierTransition(commande.getEtat(), etat);

        if (etat == EtatCommande.LIVREE) {
            // Declarer une commande livree, c'est recevoir tout ce qui restait attendu. Passer par
            // la meme operation que la reception partielle evite d'avoir deux chemins qui
            // ecrivent le stock, et qui divergeraient a la premiere correction.
            List<LigneReceptionDto> reliquat = ligneCmndeFourRepository.findAllByCommandeFournisseurId(id).stream()
                    .filter(ligne -> reste(ligne).signum() > 0)
                    .map(ligne -> {
                        LigneReceptionDto reception = new LigneReceptionDto();
                        reception.setIdLigne(ligne.getId());
                        reception.setQuantite(reste(ligne));
                        return reception;
                    })
                    .collect(Collectors.toList());
            if (!reliquat.isEmpty()) {
                return recevoir(id, reliquat);
            }
        }

        EtatCommande precedent = commande.getEtat();
        commande.setEtat(etat);
        log.info("Commande fournisseur {} : {} -> {}", id, precedent, etat);
        return CommandeFourDto.fromEntity(commandeFourRepository.save(commande));
    }

    /**
     * Enregistre ce qui est reellement arrive.
     *
     * Une commande se soldait d'un coup : recevoir 6 unites sur 10 obligeait a mentir en declarant
     * tout livre, ou a ne rien enregistrer en attendant le reste — les deux faussent le stock.
     *
     * Chaque quantite recue entre en magasin, et l'etat se deduit de ce qui reste attendu : tout
     * est arrive, la commande est livree ; il manque quelque chose, elle est partiellement livree.
     * L'etat n'est donc jamais declare par l'appelant, il est constate.
     */
    @Override
    @Transactional
    public CommandeFourDto recevoir(Long id, List<LigneReceptionDto> receptions) {
        CommandeFour commande = commande(id);

        if (commande.getEtat() != EtatCommande.VALIDEE
                && commande.getEtat() != EtatCommande.PARTIELLEMENT_LIVREE) {
            throw new InvalidEntityException(
                    "On ne reçoit une marchandise que sur une commande validée, celle-ci est "
                            + commande.getEtat(),
                    ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID);
        }
        if (receptions == null || receptions.isEmpty()) {
            throw new InvalidEntityException("Une réception porte au moins une ligne",
                    ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID);
        }

        List<LigneCmndeFournisseur> lignes = ligneCmndeFourRepository.findAllByCommandeFournisseurId(id);
        for (LigneReceptionDto reception : receptions) {
            LigneCmndeFournisseur ligne = lignes.stream()
                    .filter(l -> l.getId().equals(reception.getIdLigne()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidEntityException(
                            "La ligne " + reception.getIdLigne() + " n'appartient pas à la commande " + id,
                            ErrorCodes.LIGNE_COMMANDE_FOURNISSEUR_NOT_VALID));

            BigDecimal recue = reception.getQuantite();
            if (recue == null || recue.signum() <= 0) {
                throw new InvalidEntityException(
                        "La quantité reçue doit être strictement positive",
                        ErrorCodes.LIGNE_COMMANDE_FOURNISSEUR_NOT_VALID);
            }
            // Recevoir plus que commande n'est pas une livraison, c'est une erreur de comptage ou
            // une commande a corriger : le stock ne doit pas en porter la trace en silence.
            if (recue.compareTo(reste(ligne)) > 0) {
                throw new InvalidEntityException(
                        "La quantité reçue dépasse ce qui reste attendu : " + reste(ligne)
                                + " attendus, " + recue + " reçus",
                        ErrorCodes.LIGNE_COMMANDE_FOURNISSEUR_NOT_VALID,
                        List.of("Ligne " + ligne.getId() + " : commandé " + ligne.getQuantite()
                                + ", déjà livré " + ligne.getQuantiteLivree()));
            }

            mvtStkService.entreeStock(MvtStkDto.builder()
                    .article(ArticleDto.builder().Id(ligne.getArticles().getId()).build())
                    .quantite(recue)
                    .motif(MotifMvtStk.LIVRAISON_COMMANDE)
                    .build());
            ligne.setQuantiteLivree(dejaLivre(ligne).add(recue));
            ligneCmndeFourRepository.save(ligne);
        }

        boolean toutRecu = lignes.stream().allMatch(ligne -> reste(ligne).signum() <= 0);
        commande.setEtat(toutRecu ? EtatCommande.LIVREE : EtatCommande.PARTIELLEMENT_LIVREE);
        log.info("Commande fournisseur {} : reception de {} ligne(s), etat {}",
                id, receptions.size(), commande.getEtat());
        return CommandeFourDto.fromEntity(commandeFourRepository.save(commande));
    }

    private BigDecimal dejaLivre(LigneCmndeFournisseur ligne) {
        return ligne.getQuantiteLivree() == null ? BigDecimal.ZERO : ligne.getQuantiteLivree();
    }

    private BigDecimal reste(LigneCmndeFournisseur ligne) {
        BigDecimal commandee = ligne.getQuantite() == null ? BigDecimal.ZERO : ligne.getQuantite();
        return commandee.subtract(dejaLivre(ligne)).max(BigDecimal.ZERO);
    }

    @Override
    public List<LigneCmndeFournisseurDto> lignes(Long idCommande) {
        commande(idCommande);
        return ligneCmndeFourRepository.findAllByCommandeFournisseurId(idCommande).stream()
                .map(LigneCmndeFournisseurDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LigneCmndeFournisseurDto ajouterLigne(Long idCommande, LigneCmndeFournisseurDto ligne) {
        CommandeFour commande = commandeModifiable(idCommande);
        if (ligne == null || ligne.getArticle() == null || ligne.getArticle().getId() == null) {
            throw new InvalidEntityException("Une ligne de commande désigne un article",
                    ErrorCodes.LIGNE_COMMANDE_FOURNISSEUR_NOT_VALID);
        }
        Article article = article(ligne.getArticle().getId());

        LigneCmndeFournisseur nouvelle = new LigneCmndeFournisseur();
        nouvelle.setCommandeFournisseur(commande);
        nouvelle.setArticles(article);
        nouvelle.setQuantite(quantiteValide(ligne.getQuantite()));
        nouvelle.setPrixUnitaire(ligne.getPrixUnitaire());
        nouvelle.setIdEntreprise(commande.getIdEntreprise());

        return LigneCmndeFournisseurDto.fromEntity(ligneCmndeFourRepository.save(nouvelle));
    }

    @Override
    @Transactional
    public LigneCmndeFournisseurDto modifierQuantite(Long idCommande, Long idLigne, BigDecimal quantite) {
        commandeModifiable(idCommande);
        LigneCmndeFournisseur ligne = ligne(idCommande, idLigne);
        ligne.setQuantite(quantiteValide(quantite));
        return LigneCmndeFournisseurDto.fromEntity(ligneCmndeFourRepository.save(ligne));
    }

    @Override
    @Transactional
    public void retirerLigne(Long idCommande, Long idLigne) {
        commandeModifiable(idCommande);
        ligneCmndeFourRepository.delete(ligne(idCommande, idLigne));
    }

    private CommandeFour commande(Long id) {
        CommandeFour commande = commandeFourRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune commande fournisseur avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.COMMANDE_FOURNISSEUR_NOT_FOUND));
        cloisonnement.verifierAcces(commande.getIdEntreprise(), "commande fournisseur", id);
        return commande;
    }

    /**
     * Une commande ne se corrige que tant qu'elle n'est pas figee. Livree, ses lignes ont deja
     * fait entrer la marchandise et les toucher ferait mentir le stock ; annulee, elle n'a plus
     * a changer — c'est une trace.
     */
    private CommandeFour commandeModifiable(Long id) {
        CommandeFour commande = commande(id);
        // `estEngagee` et non `estTerminal` : une commande dont une partie est deja arrivee ne se
        // corrige plus non plus, sans quoi le reste attendu ne voudrait plus rien dire.
        if (commande.getEtat().estEngagee()) {
            throw new InvalidEntityException(
                    "Une commande " + commande.getEtat() + " ne se modifie plus",
                    ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID,
                    List.of("L'état " + commande.getEtat() + " est définitif"));
        }
        return commande;
    }

    /**
     * La ligne doit appartenir a la commande : sans ce controle, connaitre un identifiant de
     * ligne suffirait pour modifier la commande d'un autre.
     */
    private LigneCmndeFournisseur ligne(Long idCommande, Long idLigne) {
        LigneCmndeFournisseur ligne = ligneCmndeFourRepository.findById(idLigne)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune ligne avec l'identifiant " + idLigne + " n'a été trouvée",
                        ErrorCodes.LIGNE_COMMANDE_FOURNISSEUR_NOT_FOUND));
        if (ligne.getCommandeFournisseur() == null
                || !idCommande.equals(ligne.getCommandeFournisseur().getId())) {
            throw new InvalidEntityException(
                    "La ligne " + idLigne + " n'appartient pas à la commande " + idCommande,
                    ErrorCodes.LIGNE_COMMANDE_FOURNISSEUR_NOT_VALID);
        }
        return ligne;
    }

    private BigDecimal quantiteValide(BigDecimal quantite) {
        if (quantite == null || quantite.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEntityException(
                    "La quantité d'une ligne de commande doit être strictement positive",
                    ErrorCodes.LIGNE_COMMANDE_FOURNISSEUR_NOT_VALID);
        }
        return quantite;
    }

    private Article article(Long idArticle) {
        return articleRepository.findById(idArticle)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + idArticle + " n'a été trouvé",
                        ErrorCodes.ARTICLE_NOT_FOUND));
    }

    private void verifierTransition(EtatCommande actuel, EtatCommande cible) {
        if (cible == null) {
            throw new InvalidEntityException("L'état visé doit être renseigné",
                    ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID);
        }
        if (!actuel.peutPasserA(cible)) {
            throw new InvalidEntityException(
                    "Une commande " + actuel + " ne peut pas passer à " + cible,
                    ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID,
                    List.of(actuel.estTerminal()
                            ? "L'état " + actuel + " est définitif"
                            : "Transition interdite : " + actuel + " vers " + cible));
        }
    }

    @Override
    public CommandeFourDto findById(Long id) {
        if(id==null){
            log.error("Commande client Id is null");
            return null;
        }
        return  java.util.Optional.of(commande(id))
                .map(CommandeFourDto::fromEntity)
                // Le « {} » d'un journal SLF4J etait reste dans une concatenation.
                .orElseThrow(()->new EntityNotFoundException("Aucune commande fournisseur avec l'identifiant "+id+" n'a été trouvée",ErrorCodes.COMMANDE_FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public CommandeFourDto findByCode(String code) {
        if(!StringUtils.hasLength(code)){
            log.error("Commande Fournisseur Id is Null");
            return null;
        }
        return (cloisonnement.filtre()
                ? commandeFourRepository.findCommandeFourByCodeAndIdEntreprise(code, cloisonnement.entrepriseCourante())
                : commandeFourRepository.findCommandeFourByCode(code))
                .map(CommandeFourDto::fromEntity)
                .orElseThrow(()->new EntityNotFoundException("Aucune commande fournisseur avec le code "+code+" n'a été trouvée",ErrorCodes.COMMANDE_FOURNISSEUR_NOT_FOUND));
    }

    @Override
    public List<CommandeFourDto> findAll() {
        return (cloisonnement.filtre()
                ? commandeFourRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante())
                : commandeFourRepository.findAll()).stream()
                .map(CommandeFourDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Commande Fournisseur ID is null");
            return;
        }
        commandeFourRepository.deleteById(id);
    }
}
