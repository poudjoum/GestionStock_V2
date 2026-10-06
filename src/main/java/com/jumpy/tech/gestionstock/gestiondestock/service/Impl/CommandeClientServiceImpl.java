package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.conditionnement.Conditionnements;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCommandeClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeClient;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import com.jumpy.tech.gestionstock.gestiondestock.repository.SiteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.site.SiteCourant;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeClient;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CommandeClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.CommandeClientService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.CommandeClientValidator;
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

public class CommandeClientServiceImpl implements CommandeClientService {

    private CommandeClientRepository commandeClientRepository;
    private final Cloisonnement cloisonnement;
    private ClientRepository clientRepository;
    private ArticleRepository articleRepository;
    private LigneCmndeClientRepository ligneCmndeClientRepository;
    private final Conditionnements conditionnements;
    private final SiteCourant siteCourant;
    private final SiteRepository siteRepository;
    public CommandeClientServiceImpl(CommandeClientRepository commandeClientRepository, ClientRepository clientRepository,ArticleRepository articleRepository,LigneCmndeClientRepository ligneCmndeClientRepository,Cloisonnement cloisonnement,
                                     Conditionnements conditionnements, SiteCourant siteCourant,
                                     SiteRepository siteRepository){
        this.siteCourant=siteCourant;
        this.siteRepository=siteRepository;
        this.conditionnements=conditionnements;
        this.commandeClientRepository=commandeClientRepository;
        this.articleRepository=articleRepository;
        this.clientRepository=clientRepository;
        this.ligneCmndeClientRepository=ligneCmndeClientRepository;
        this.cloisonnement=cloisonnement;
    }


    /**
     * La commande et ses lignes sont desormais ecrites dans une seule transaction : une panne
     * entre les deux laissait jusqu'ici une commande sans contenu, indistinguable d'une commande
     * vide.
     *
     * Aucun mouvement de stock ici, volontairement : une commande client est un engagement, pas
     * une sortie de marchandise. Le stock diminue a la vente (cf. VenteServiceImpl), qui est le
     * moment ou l'article quitte reellement le magasin.
     */
    @Override
    @Transactional
    public CommandeClientDto save(CommandeClientDto dto) {
        List<String> errors= CommandeClientValidator.validate(dto);
        if(!errors.isEmpty()){
            log.error("Commande client n'est pas valide");
            throw new InvalidEntityException("La commande client n'est pas valide", ErrorCodes.COMMANDE_CLIENT_NOT_VALID,errors);
        }
        Optional<Client> client=clientRepository.findById(dto.getClient().getId());
        if(client.isEmpty()){
            log.warn("Client with Id {} was not in database",dto.getClient().getId());
            throw new EntityNotFoundException("Aucun client avec l'identifiant "+dto.getClient().getId()+" n'a été trouvé",
                    ErrorCodes.CLIENT_NOT_FOUND);
        }
        List<String> articleErrors=new ArrayList<>();
        if(dto.getLigneCmndeClients()!=null){
            dto.getLigneCmndeClients().forEach(ligCmdClt->{
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
        CommandeClient aEnregistrer = CommandeClientDto.toEntity(dto);
        aEnregistrer.setEtat(EtatCommande.EN_PREPARATION);
        if (cloisonnement.filtre()) {
            aEnregistrer.setIdEntreprise(cloisonnement.entrepriseCourante());
        }
        Site magasin = siteCourant.site();
        if (magasin != null && !magasin.vend()) {
            throw new InvalidEntityException(
                    "« " + magasin.getNom() + " » est un entrepôt : la commande se prend au magasin qui vend",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
                    List.of("Choisissez le magasin du client, puis l'entrepôt comme site qui livre"));
        }
        aEnregistrer.setSite(magasin);
        aEnregistrer.setSiteExpedition(dto.getIdSiteExpedition() == null ? magasin
                : siteDeLEntreprise(dto.getIdSiteExpedition(), aEnregistrer.getIdEntreprise()));
        CommandeClient saveCmndClt=commandeClientRepository.save(aEnregistrer);
        if(dto.getLigneCmndeClients()!=null) {
            dto.getLigneCmndeClients().forEach(ligCmdClt -> {
                LigneCmndeClient ligneCmndeClient = LigneCommandeClientDto.toEntity(ligCmdClt);
                Article article = articleRepository.findById(ligCmdClt.getArticle().getId()).orElseThrow();
                ligneCmndeClient.setArticles(article);
                conditionner(ligneCmndeClient, article, ligCmdClt.getConditionnement());
                ligneCmndeClient.setCommandeClient(saveCmndClt);
                // Comme les lignes ajoutees apres coup : l'entreprise de leur commande.
                ligneCmndeClient.setIdEntreprise(saveCmndClt.getIdEntreprise());
                ligneCmndeClientRepository.save(ligneCmndeClient);
            });
        }
        return CommandeClientDto.fromEntity(saveCmndClt);
    }

    @Override
    public CommandeClientDto findById(Long id) {

        if(id==null){
            log.error("Commande client Id is null");
            return null;
        }
    return  java.util.Optional.of(commande(id))
            .map(CommandeClientDto::fromEntity)
            // Le « {} » d'un journal SLF4J etait reste dans une concatenation : le client lisait
            // « Aucune Commande avec l'id {}12 ».
            .orElseThrow(()->new EntityNotFoundException("Aucune commande client avec l'identifiant "+id+" n'a été trouvée",ErrorCodes.COMMANDE_CLIENT_NOT_FOUND));
    }

    @Override
    public CommandeClientDto findByCode(String code) {
        if(!StringUtils.hasLength(code)){
            log.error("Commande client Id is Null");
            return null;
        }
        return (cloisonnement.filtre()
                ? commandeClientRepository.findCommandeClientByCodeAndIdEntreprise(code, cloisonnement.entrepriseCourante())
                : commandeClientRepository.findCommandeClientByCode(code))
                .map(CommandeClientDto::fromEntity)
                .orElseThrow(()->new EntityNotFoundException("Aucune commande client avec le code "+code+" n'a été trouvée",ErrorCodes.COMMANDE_CLIENT_NOT_FOUND));
    }

    /**
     * Les commandes de l'entreprise. Un collaborateur attache a des sites voit celles qu'ils
     * prennent ou qu'ils livrent : l'equipe de l'entrepot y trouve ce qu'elle a a preparer.
     */
    @Override
    public org.springframework.data.domain.Page<CommandeClientDto> rechercher(
            List<EtatCommande> etats, String q, org.springframework.data.domain.Pageable pageable) {
        Long entreprise = cloisonnement.entrepriseCourante();
        boolean tous = siteCourant.voitTousLesSites();
        // Une liste vide dans `in` n'est pas comprise de toutes les bases : un identifiant qui ne
        // designe aucun site tient lieu de « aucun ».
        List<Long> sites = tous || entreprise == null ? List.of(-1L)
                : siteRepository.findAllByIdEntrepriseOrderByPrincipalDescNomAsc(entreprise).stream()
                        .filter(siteCourant::peutVoir).map(com.jumpy.tech.gestionstock.gestiondestock.entities.Site::getId)
                        .collect(Collectors.toList());
        return commandeClientRepository.rechercher(
                        cloisonnement.filtre(),
                        cloisonnement.filtre() ? entreprise : null,
                        etats == null || etats.isEmpty() ? null : etats,
                        tous,
                        sites.isEmpty() ? List.of(-1L) : sites,
                        com.jumpy.tech.gestionstock.gestiondestock.service.Impl.RechercheUtils.normaliser(q),
                        pageable)
                .map(CommandeClientDto::fromEntity);
    }

    @Override
    public List<CommandeClientDto> findAll() {
        return (cloisonnement.filtre()
                ? commandeClientRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante())
                : commandeClientRepository.findAll()).stream()
                .filter(c -> siteCourant.voitTousLesSites()
                        || (c.getSite() != null && siteCourant.peutVoir(c.getSite()))
                        || (c.getSiteExpedition() != null && siteCourant.peutVoir(c.getSiteExpedition())))
                .map(CommandeClientDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Change le site qui livrera : le magasin n'a pas de quoi, l'entrepot s'en charge. Tant que
     * rien n'est parti — une commande servie en partie a deja fait sortir sa marchandise d'un site.
     */
    @Override
    @Transactional
    public CommandeClientDto changerSiteExpedition(Long id, Long idSite) {
        CommandeClient commande = commande(id);
        if (commande.getEtat() != EtatCommande.EN_PREPARATION && commande.getEtat() != EtatCommande.VALIDEE) {
            throw new InvalidEntityException(
                    "Une commande " + commande.getEtat() + " ne change plus de site de livraison",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID);
        }
        commande.setSiteExpedition(siteDeLEntreprise(idSite, commande.getIdEntreprise()));
        return CommandeClientDto.fromEntity(commandeClientRepository.save(commande));
    }

    /**
     * Un site actif de l'entreprise. Le magasin peut faire livrer par un site ou il ne travaille
     * pas — c'est tout l'interet de l'entrepot ; c'est l'equipe de ce site qui servira.
     */
    private Site siteDeLEntreprise(Long idSite, Long idEntreprise) {
        return siteRepository.findById(idSite)
                .filter(s -> java.util.Objects.equals(s.getIdEntreprise(), idEntreprise) && s.isActif())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun site avec l'identifiant " + idSite + " n'a été trouvé", ErrorCodes.SITE_NOT_FOUND));
    }

    /**
     * Fait avancer la commande dans son cycle de vie.
     *
     * Aucune transition n'ecrit de mouvement, pas meme la livraison : c'est la vente qui sort la
     * marchandise du magasin. La decompter ici aussi la retirerait deux fois.
     */
    @Override
    @Transactional
    public CommandeClientDto mettreAJourEtat(Long id, EtatCommande etat) {
        // Par `commande` et non par le repository : cette methode etait la derniere a lire une
        // commande sans passer par le cloisonnement, et faire avancer l'etat de la commande d'une
        // autre entreprise suffisait a en connaitre l'identifiant.
        CommandeClient commande = commande(id);

        if (etat == null) {
            throw new InvalidEntityException("L'état visé doit être renseigné",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID);
        }
        // La cloture passe par son propre geste : declaree ici, elle arriverait sans motif, et
        // l'etat ne dirait plus que « on n'attend plus rien » sans dire pourquoi.
        if (etat == EtatCommande.CLOTUREE) {
            throw new InvalidEntityException(
                    "La clôture d'un reliquat se demande avec son motif",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
                    List.of("POST /commandes-clients/" + id + "/cloture"));
        }
        if (!commande.getEtat().peutPasserA(etat)) {
            throw new InvalidEntityException(
                    "Une commande " + commande.getEtat() + " ne peut pas passer à " + etat,
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
                    List.of(commande.getEtat().estTerminal()
                            ? "L'état " + commande.getEtat() + " est définitif"
                            : "Transition interdite : " + commande.getEtat() + " vers " + etat));
        }

        commande.setEtat(etat);
        return CommandeClientDto.fromEntity(commandeClientRepository.save(commande));
    }

    /**
     * Cesse de devoir le reliquat.
     *
     * Aucun mouvement de stock, pour la meme raison qu'a la livraison : c'est la vente qui sort la
     * marchandise, et ce qui n'a jamais ete servi n'est jamais sorti. Les lignes restent
     * intactes — l'ecart entre le commande et le vendu dit ce qui n'a pas ete honore.
     */
    @Override
    @Transactional
    public CommandeClientDto cloturer(Long id, String motif) {
        CommandeClient commande = commande(id);
        if (!commande.getEtat().peutPasserA(EtatCommande.CLOTUREE)) {
            throw new InvalidEntityException(
                    "Une commande " + commande.getEtat() + " ne se clôture pas",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
                    List.of(commande.getEtat().estTerminal()
                            ? "L'état " + commande.getEtat() + " est définitif"
                            : "Seule une commande partiellement livrée a un reliquat à clôturer"));
        }
        if (!StringUtils.hasText(motif)) {
            throw new InvalidEntityException("Le motif de clôture est obligatoire",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
                    List.of("Sans motif, on ne saura plus pourquoi le reliquat a été abandonné"));
        }

        commande.setEtat(EtatCommande.CLOTUREE);
        commande.setMotifCloture(motif.trim());
        log.info("Commande client {} : cloturee ({})", id, commande.getMotifCloture());
        return CommandeClientDto.fromEntity(commandeClientRepository.save(commande));
    }

    @Override
    public List<LigneCommandeClientDto> lignes(Long idCommande) {
        commande(idCommande);
        return ligneCmndeClientRepository.findAllByCommandeClientId(idCommande).stream()
                .map(LigneCommandeClientDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LigneCommandeClientDto ajouterLigne(Long idCommande, LigneCommandeClientDto ligne) {
        CommandeClient commande = commandeModifiable(idCommande);
        if (ligne == null || ligne.getArticle() == null || ligne.getArticle().getId() == null) {
            throw new InvalidEntityException("Une ligne de commande désigne un article",
                    ErrorCodes.LIGNE_COMMANDE_CLIENT_NOT_VALID);
        }
        Article article = articleRepository.findById(ligne.getArticle().getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + ligne.getArticle().getId() + " n'a été trouvé",
                        ErrorCodes.ARTICLE_NOT_FOUND));

        LigneCmndeClient nouvelle = new LigneCmndeClient();
        nouvelle.setCommandeClient(commande);
        nouvelle.setArticles(article);
        nouvelle.setQuantite(quantiteValide(ligne.getQuantite()));
        nouvelle.setPrixUnitaire(ligne.getPrixUnitaire());
        conditionner(nouvelle, article, ligne.getConditionnement());
        nouvelle.setIdEntreprise(commande.getIdEntreprise());

        return LigneCommandeClientDto.fromEntity(ligneCmndeClientRepository.save(nouvelle));
    }

    /**
     * Le conditionnement commande, et la contenance qu'il fige : la commande se servira dans ces
     * cartons-la. Sans prix envoye, la ligne prend celui du catalogue — celui du carton s'il
     * s'agit d'un carton, et non celui de la bouteille.
     */
    private void conditionner(LigneCmndeClient ligne, Article article, ConditionnementDto demande) {
        Conditionnement conditionnement = conditionnements.pourLigne(article, demande, Conditionnements.Usage.VENTE);
        Conditionnements.verifierFraction(article, conditionnement, ligne.getQuantite(),
                ErrorCodes.LIGNE_COMMANDE_CLIENT_NOT_VALID);
        ligne.setConditionnement(conditionnement);
        ligne.setContenance(Conditionnements.contenance(conditionnement));
        if (ligne.getPrixUnitaire() == null) {
            ligne.setPrixUnitaire(Conditionnements.prixCatalogue(article, conditionnement));
        }
    }

    @Override
    @Transactional
    public LigneCommandeClientDto modifierQuantite(Long idCommande, Long idLigne, BigDecimal quantite) {
        commandeModifiable(idCommande);
        LigneCmndeClient ligne = ligne(idCommande, idLigne);
        ligne.setQuantite(quantiteValide(quantite));
        Conditionnements.verifierFraction(ligne.getArticles(), ligne.getConditionnement(), quantite,
                ErrorCodes.LIGNE_COMMANDE_CLIENT_NOT_VALID);
        return LigneCommandeClientDto.fromEntity(ligneCmndeClientRepository.save(ligne));
    }

    @Override
    @Transactional
    public void retirerLigne(Long idCommande, Long idLigne) {
        commandeModifiable(idCommande);
        ligneCmndeClientRepository.delete(ligne(idCommande, idLigne));
    }

    private CommandeClient commande(Long id) {
        CommandeClient commande = commandeClientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune commande client avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.COMMANDE_CLIENT_NOT_FOUND));
        cloisonnement.verifierAcces(commande.getIdEntreprise(), "commande client", id);
        return commande;
    }

    /**
     * Une commande figee — livree, cloturee ou annulee — ne se corrige plus : c'est une trace.
     *
     * `estEngagee` et non `estTerminal` : une commande partiellement servie restait modifiable,
     * et baisser la quantite d'une ligne deja servie pour partie faisait mentir le reliquat — le
     * reste du se calcule par difference entre ce qui est commande et ce qui est vendu.
     */
    private CommandeClient commandeModifiable(Long id) {
        CommandeClient commande = commande(id);
        if (commande.getEtat().estEngagee()) {
            throw new InvalidEntityException(
                    "Une commande " + commande.getEtat() + " ne se modifie plus",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
                    List.of("L'état " + commande.getEtat() + " est définitif"));
        }
        return commande;
    }

    private LigneCmndeClient ligne(Long idCommande, Long idLigne) {
        LigneCmndeClient ligne = ligneCmndeClientRepository.findById(idLigne)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune ligne avec l'identifiant " + idLigne + " n'a été trouvée",
                        ErrorCodes.LIGNE_COMMANDE_CLIENT_NOT_FOUND));
        // Sans ce controle, connaitre un identifiant de ligne suffirait a modifier la commande
        // d'un autre.
        if (ligne.getCommandeClient() == null
                || !idCommande.equals(ligne.getCommandeClient().getId())) {
            throw new InvalidEntityException(
                    "La ligne " + idLigne + " n'appartient pas à la commande " + idCommande,
                    ErrorCodes.LIGNE_COMMANDE_CLIENT_NOT_VALID);
        }
        return ligne;
    }

    private BigDecimal quantiteValide(BigDecimal quantite) {
        if (quantite == null || quantite.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEntityException(
                    "La quantité d'une ligne de commande doit être strictement positive",
                    ErrorCodes.LIGNE_COMMANDE_CLIENT_NOT_VALID);
        }
        return quantite;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(id==null){
            log.error("Commande Client ID is null");
            return;
        }
        commandeClientRepository.deleteById(id);
    }
}
