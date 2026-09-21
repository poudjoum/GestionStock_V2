package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.MvtStkDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeClient;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeClient;
import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import com.jumpy.tech.gestionstock.gestiondestock.entities.MotifMvtStk;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CommandeClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneVenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.MvtStkService;
import com.jumpy.tech.gestionstock.gestiondestock.service.VenteService;
import com.jumpy.tech.gestionstock.gestiondestock.validator.VenteValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VenteServiceImpl implements VenteService {

    private final VenteRepository venteRepository;
    private final ArticleRepository articleRepository;
    private final LigneVenteRepository ligneVenteRepository;
    private final FactureRepository factureRepository;
    private final CommandeClientRepository commandeClientRepository;
    private final LigneCmndeClientRepository ligneCmndeClientRepository;
    private final ClientRepository clientRepository;
    private final Cloisonnement cloisonnement;
    private final MvtStkService mvtStkService;

    public VenteServiceImpl(VenteRepository venteRepository, ArticleRepository articleRepository,
                            LigneVenteRepository ligneVenteRepository,
                            FactureRepository factureRepository,
                            CommandeClientRepository commandeClientRepository,
                            LigneCmndeClientRepository ligneCmndeClientRepository,
                            ClientRepository clientRepository,
                            Cloisonnement cloisonnement,
                            MvtStkService mvtStkService) {
        this.clientRepository = clientRepository;
        this.cloisonnement = cloisonnement;
        this.venteRepository = venteRepository;
        this.articleRepository = articleRepository;
        this.ligneVenteRepository = ligneVenteRepository;
        this.factureRepository = factureRepository;
        this.commandeClientRepository = commandeClientRepository;
        this.ligneCmndeClientRepository = ligneCmndeClientRepository;
        this.mvtStkService = mvtStkService;
    }

    /**
     * Enregistre la vente, ses lignes, et la sortie de stock de chaque ligne.
     *
     * Trois choses manquaient. Les lignes n'etaient jamais ecrites — `ligneVenteRepository` etait
     * injecte sans etre appele — donc une vente se resumait a un en-tete sans contenu. Aucun
     * mouvement de stock n'etait enregistre, donc vendre ne retirait rien du magasin. Et les deux
     * ecritures n'avaient pas de transaction commune.
     *
     * Le tout est maintenant dans une seule transaction : si une ligne manque de stock, la vente
     * entiere est annulee. Vendre la moitie d'un panier sans le dire serait pire que refuser.
     */
    @Override
    @Transactional
    public VenteDto save(VenteDto dto) {
        List<String> errors = VenteValidator.validate(dto);
        if (!errors.isEmpty()) {
            log.error("Vente not Valid {}", dto);
            // Le code rendu etait VENTE_NOT_FOUND pour une vente invalide.
            throw new InvalidEntityException("La vente n'est pas valide", ErrorCodes.VENTE_NOT_VALID, errors);
        }

        List<LigneVenteDto> lignes = dto.getLigneVente() == null ? List.of() : dto.getLigneVente();
        if (lignes.isEmpty()) {
            throw new InvalidEntityException("Une vente sans ligne ne vend rien",
                    ErrorCodes.VENTE_NOT_VALID, List.of("Aucune ligne de vente fournie"));
        }

        List<String> articleErrors = new ArrayList<>();
        for (LigneVenteDto ligne : lignes) {
            if (ligne.getArticle() == null || ligne.getArticle().getId() == null) {
                articleErrors.add("Impossible d'enregistrer une vente sans article");
                continue;
            }
            Optional<Article> article = articleRepository.findById(ligne.getArticle().getId());
            if (article.isEmpty()) {
                articleErrors.add("L'article avec l'identifiant " + ligne.getArticle().getId()
                        + " n'existe pas");
            }
        }
        if (!articleErrors.isEmpty()) {
            log.error("One or more article were not in Database, {}", articleErrors);
            // Les erreurs remontees etaient `errors`, la liste de validation, toujours vide a ce
            // stade : le client recevait un 400 sans savoir quel article posait probleme.
            throw new InvalidEntityException(
                    "Un ou plusieurs articles de la vente n'existent pas",
                    ErrorCodes.VENTE_NOT_VALID, articleErrors);
        }

        Vente aEnregistrer = VenteDto.toEntity(dto);
        // L'entreprise vient du compte connecte et ecrase ce que dirait la requete : une vente ne
        // s'enregistre pas chez le voisin.
        if (cloisonnement.filtre()) {
            aEnregistrer.setIdEntreprise(cloisonnement.entrepriseCourante());
        }
        // Le client est facultatif : la vente de comptoir anonyme reste le cas ordinaire.
        if (dto.getClient() != null && dto.getClient().getId() != null) {
            aEnregistrer.setClient(client(dto.getClient().getId()));
        }
        Vente savedVente = venteRepository.save(aEnregistrer);

        for (LigneVenteDto ligneDto : lignes) {
            LigneVente ligne = LigneVenteDto.toEntity(ligneDto);
            ligne.setVente(savedVente);
            ligneVenteRepository.save(ligne);
            sortirDuStock(ligneDto, savedVente);
        }

        return VenteDto.fromEntity(savedVente);
    }

    /**
     * La vente est le moment ou la marchandise quitte le magasin ; c'est donc ici, et pas a la
     * commande client, que le stock diminue. Une commande n'est qu'un engagement : tant qu'elle
     * n'est pas servie, rien n'est sorti des rayons.
     */
    private void sortirDuStock(LigneVenteDto ligne, Vente vente) {
        mvtStkService.sortieStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ligne.getArticle().getId()).build())
                .quantite(ligne.getQuantite())
                .motif(MotifMvtStk.VENTE)
                .idEntreprise(vente.getIdEntreprise())
                .build());
    }

    @Override
    public List<LigneVenteDto> lignes(Long idVente) {
        vente(idVente);
        return ligneVenteRepository.findAllByVenteId(idVente).stream()
                .map(LigneVenteDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Ajoute un article a une vente en cours. C'est le geste du comptoir : la vente se construit
     * article par article, sans qu'il faille connaitre tout le panier d'avance.
     *
     * La sortie de stock est immediate, comme a l'enregistrement de la vente, et porte le meme
     * motif : ce n'est pas une correction, c'est de la marchandise vendue.
     */
    @Override
    @Transactional
    public LigneVenteDto ajouterLigne(Long idVente, LigneVenteDto ligne) {
        Vente vente = venteModifiable(idVente);
        if (ligne == null || ligne.getArticle() == null || ligne.getArticle().getId() == null) {
            throw new InvalidEntityException("Une ligne de vente désigne un article",
                    ErrorCodes.LIGNE_VENTE_NOT_VALID);
        }
        Article article = articleRepository.findById(ligne.getArticle().getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + ligne.getArticle().getId() + " n'a été trouvé",
                        ErrorCodes.ARTICLE_NOT_FOUND));
        BigDecimal quantite = quantiteValide(ligne.getQuantite());

        // Le stock est debite avant l'ecriture de la ligne : s'il manque, la transaction echoue et
        // la vente reste telle qu'elle etait.
        mvtStkService.sortieStock(MvtStkDto.builder()
                .article(ArticleDto.builder().Id(article.getId()).build())
                .quantite(quantite)
                .motif(MotifMvtStk.VENTE)
                .build());

        LigneVente nouvelle = new LigneVente();
        nouvelle.setVente(vente);
        nouvelle.setArticles(article);
        nouvelle.setQuantite(quantite);
        nouvelle.setPrixUnitaire(ligne.getPrixUnitaire() != null
                ? ligne.getPrixUnitaire() : article.getPrixUnitaire());
        nouvelle.setIdEntreprise(vente.getIdEntreprise());

        return LigneVenteDto.fromEntity(ligneVenteRepository.save(nouvelle));
    }

    /**
     * Attribue ou change le client d'une vente.
     *
     * Le caissier ne sait pas toujours d'avance a qui il vend : le client se presente, ou se fait
     * connaitre au moment de payer. Tant que la vente n'est ni annulee ni facturee, elle peut
     * donc recevoir son nom.
     */
    @Override
    @Transactional
    public VenteDto attribuerClient(Long idVente, Long idClient) {
        Vente vente = venteModifiable(idVente);
        vente.setClient(client(idClient));
        return VenteDto.fromEntity(venteRepository.save(vente));
    }

    private Client client(Long idClient) {
        if (idClient == null) {
            throw new InvalidEntityException("Aucun client ne peut être désigné sans identifiant",
                    ErrorCodes.CLIENT_NOT_VALID);
        }
        return clientRepository.findById(idClient)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun client avec l'identifiant " + idClient + " n'a été trouvé",
                        ErrorCodes.CLIENT_NOT_FOUND));
    }

    /**
     * Cree la vente qui sert une commande client.
     *
     * La commande doit etre VALIDEE : servir une commande encore en preparation reviendrait a
     * sortir une marchandise que personne n'a confirmee. La vente reprend les lignes telles
     * qu'elles sont au moment ou on sert, et la commande passe LIVREE — ce qui la fige a son tour.
     */
    @Override
    @Transactional
    public VenteDto servirCommandeClient(Long idCommandeClient) {
        CommandeClient commande = commandeClientRepository.findById(idCommandeClient)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune commande client avec l'identifiant " + idCommandeClient + " n'a été trouvée",
                        ErrorCodes.COMMANDE_CLIENT_NOT_FOUND));

        if (commande.getEtat() != EtatCommande.VALIDEE) {
            throw new InvalidEntityException(
                    "Seule une commande VALIDEE se sert, celle-ci est " + commande.getEtat(),
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
                    List.of("Validez la commande avant de la servir"));
        }

        List<LigneCmndeClient> lignesCommande = ligneCmndeClientRepository.findAllByCommandeClientId(idCommandeClient);
        if (lignesCommande.isEmpty()) {
            throw new InvalidEntityException("Une commande sans ligne ne se sert pas",
                    ErrorCodes.COMMANDE_CLIENT_NOT_VALID);
        }

        Vente vente = new Vente();
        vente.setCode(commande.getCode());
        vente.setDatevente(Instant.now());
        vente.setIdEntreprise(commande.getIdEntreprise());
        vente.setCommandeClient(commande);
        // Le client de la commande devient celui de la vente : la lecture n'a ensuite qu'un seul
        // chemin a suivre, que la vente vienne du comptoir ou d'une commande.
        vente.setClient(commande.getClient());
        Vente enregistree = venteRepository.save(vente);

        for (LigneCmndeClient ligneCommande : lignesCommande) {
            LigneVente ligneVente = new LigneVente();
            ligneVente.setVente(enregistree);
            ligneVente.setArticles(ligneCommande.getArticles());
            ligneVente.setQuantite(ligneCommande.getQuantite());
            ligneVente.setPrixUnitaire(ligneCommande.getPrixUnitaire());
            ligneVente.setIdEntreprise(commande.getIdEntreprise());
            ligneVenteRepository.save(ligneVente);

            mvtStkService.sortieStock(MvtStkDto.builder()
                    .article(ArticleDto.builder().Id(ligneCommande.getArticles().getId()).build())
                    .quantite(ligneCommande.getQuantite())
                    .motif(MotifMvtStk.VENTE)
                    .build());
        }

        // Tout est dans la meme transaction : si une ligne manque de stock, ni la vente ni le
        // changement d'etat ne subsistent. Servir a moitie une commande sans le dire serait pire
        // que de refuser.
        commande.setEtat(EtatCommande.LIVREE);
        commandeClientRepository.save(commande);

        log.info("Commande client {} servie par la vente {}", idCommandeClient, enregistree.getId());
        return VenteDto.fromEntity(enregistree);
    }

    @Override
    @Transactional
    public VenteDto annuler(Long idVente) {
        Vente vente = venteModifiable(idVente);

        // Chaque ligne retourne en magasin. Le motif distingue cette entree d'une livraison :
        // sans lui, l'historique d'un article montrerait deux entrees identiques dont l'une
        // n'a jamais rien fait venir du fournisseur.
        ligneVenteRepository.findAllByVenteId(idVente).forEach(ligne ->
                mvtStkService.entreeStock(MvtStkDto.builder()
                        .article(ArticleDto.builder().Id(ligne.getArticles().getId()).build())
                        .quantite(ligne.getQuantite())
                        .motif(MotifMvtStk.ANNULATION_VENTE)
                        .build()));

        // La vente reste en base : une recette encaissee puis rendue doit pouvoir se retrouver.
        vente.setAnnulee(true);
        return VenteDto.fromEntity(venteRepository.save(vente));
    }

    @Override
    @Transactional
    public LigneVenteDto modifierQuantite(Long idVente, Long idLigne, BigDecimal quantite) {
        venteModifiable(idVente);
        LigneVente ligne = ligne(idVente, idLigne);
        BigDecimal nouvelle = quantiteValide(quantite);
        BigDecimal ancienne = ligne.getQuantite();

        // La marchandise est deja sortie : seule la difference se rattrape. Augmenter sort le
        // complement — et echoue si le magasin ne l'a pas, ce qui est bien le comportement
        // attendu ; diminuer remet la difference.
        int comparaison = nouvelle.compareTo(ancienne);
        if (comparaison > 0) {
            mvtStkService.sortieStock(mouvementDe(ligne, nouvelle.subtract(ancienne)));
        } else if (comparaison < 0) {
            mvtStkService.entreeStock(mouvementDe(ligne, ancienne.subtract(nouvelle)));
        }

        ligne.setQuantite(nouvelle);
        return LigneVenteDto.fromEntity(ligneVenteRepository.save(ligne));
    }

    @Override
    @Transactional
    public void retirerLigne(Long idVente, Long idLigne) {
        venteModifiable(idVente);
        LigneVente ligne = ligne(idVente, idLigne);
        mvtStkService.entreeStock(mouvementDe(ligne, ligne.getQuantite()));
        ligneVenteRepository.delete(ligne);
    }

    private MvtStkDto mouvementDe(LigneVente ligne, BigDecimal quantite) {
        return MvtStkDto.builder()
                .article(ArticleDto.builder().Id(ligne.getArticles().getId()).build())
                .quantite(quantite)
                .motif(MotifMvtStk.CORRECTION_VENTE)
                .build();
    }

    private Vente vente(Long id) {
        if (id == null) {
            throw new InvalidEntityException("Aucune vente ne peut être cherchée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        Vente vente = venteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
        cloisonnement.verifierAcces(vente.getIdEntreprise(), "vente", id);
        return vente;
    }

    /**
     * Une vente annulee a deja rendu sa marchandise : la corriger la rendrait une seconde fois.
     * Une vente facturee est figee pour une autre raison — la facture porte des montants remis au
     * client, et changer la vente sous elle la ferait mentir. Annuler la facture rouvre la vente.
     */
    private Vente venteModifiable(Long id) {
        Vente vente = vente(id);
        if (vente.isAnnulee()) {
            throw new InvalidEntityException("Une vente annulée ne se modifie plus",
                    ErrorCodes.VENTE_NOT_VALID,
                    List.of("La vente " + id + " a déjà été annulée"));
        }
        factureRepository.findByVenteId(id)
                .filter(facture -> !facture.isAnnulee())
                .ifPresent(facture -> {
                    throw new InvalidEntityException("Une vente facturée ne se modifie plus",
                            ErrorCodes.VENTE_NOT_VALID,
                            List.of("La facture " + facture.getNumero()
                                    + " doit être annulée avant de reprendre cette vente"));
                });
        return vente;
    }

    private LigneVente ligne(Long idVente, Long idLigne) {
        LigneVente ligne = ligneVenteRepository.findById(idLigne)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune ligne avec l'identifiant " + idLigne + " n'a été trouvée",
                        ErrorCodes.LIGNE_VENTE_NOT_FOUND));
        if (ligne.getVente() == null || !idVente.equals(ligne.getVente().getId())) {
            throw new InvalidEntityException(
                    "La ligne " + idLigne + " n'appartient pas à la vente " + idVente,
                    ErrorCodes.LIGNE_VENTE_NOT_VALID);
        }
        return ligne;
    }

    private BigDecimal quantiteValide(BigDecimal quantite) {
        if (quantite == null || quantite.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEntityException(
                    "La quantité d'une ligne de vente doit être strictement positive",
                    ErrorCodes.LIGNE_VENTE_NOT_VALID);
        }
        return quantite;
    }

    @Override
    public VenteDto findById(Long id) {
        if (id == null) {
            log.error("Vente id is null");
            throw new InvalidEntityException("Aucune vente ne peut être cherchée sans identifiant",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return venteRepository.findById(id)
                .map(VenteDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec l'identifiant " + id + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
    }

    @Override
    public List<VenteDto> findAll() {
        return (cloisonnement.filtre()
                ? venteRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante())
                : venteRepository.findAll()).stream()
                .map(VenteDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<VenteDto> findAll(Pageable pageable) {
        return (cloisonnement.filtre()
                ? venteRepository.findAllByIdEntreprise(cloisonnement.entrepriseCourante(), pageable)
                : venteRepository.findAll(pageable))
                .map(VenteDto::fromEntity);
    }

    @Override
    public VenteDto findVenteByCode(String codeVente) {
        if (!StringUtils.hasLength(codeVente)) {
            log.error("Le code Vente est vide");
            throw new InvalidEntityException("Aucune vente ne peut être cherchée sans code",
                    ErrorCodes.VENTE_NOT_VALID);
        }
        return (cloisonnement.filtre()
                ? venteRepository.findVenteByCodeAndIdEntreprise(codeVente, cloisonnement.entrepriseCourante())
                : venteRepository.findVenteByCode(codeVente))
                .map(VenteDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucune vente avec le code " + codeVente + " n'a été trouvée",
                        ErrorCodes.VENTE_NOT_FOUND));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == null) {
            log.error("Vente Id est null");
            return;
        }
        venteRepository.deleteById(id);
    }
}
