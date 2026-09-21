package com.jumpy.tech.gestionstock.gestiondestock.service;


import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneVenteDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.VenteDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface VenteService {
    VenteDto save(VenteDto dto);

    /**
     * Enregistre une vente qui a deja eu lieu, sur un poste sans reseau.
     *
     * Trois choses la distinguent d'une vente ordinaire, et une seule raison : elle est un fait a
     * constater, non une transaction a autoriser. La marchandise est partie.
     *
     * Elle porte une reference tiree par le poste de vente, et reposter la meme rend la vente
     * deja enregistree plutot que d'en creer une seconde. Un telephone qui perd le reseau au
     * milieu d'un envoi ne sait pas si l'envoi est passe : il reessaie.
     *
     * Elle porte sa date reelle, et non celle de l'envoi — une vente de 9 h synchronisee a midi
     * doit peser sur la caisse de 9 h.
     *
     * Le stock ne s'y oppose pas. Deux caisses vendent hors ligne le dernier sac de ciment ; la
     * seconde serait refusee, alors que le sac est parti. Refuser n'empecherait rien : cela
     * effacerait seulement la trace de ce qui a eu lieu. Le stock passe sous zero, et l'article
     * remonte dans /stock/alertes comme reclamant un comptage.
     */
    VenteDto synchroniser(VenteDto dto);
    VenteDto findById(Long id);
    List<VenteDto> findAll();
    Page<VenteDto> findAll(Pageable pageable);
    VenteDto findVenteByCode(String codeVente);

    /** Les lignes d'une vente ; VenteDto.fromEntity ne remonte que l'en-tete. */
    List<LigneVenteDto> lignes(Long idVente);

    /**
     * Ajoute un article a une vente deja enregistree, et sort sa quantite du magasin.
     *
     * C'est le geste du comptoir : le caissier ouvre une vente et y ajoute les articles au fur et
     * a mesure qu'ils se presentent. Sans cette operation, il fallait connaitre tout le panier
     * avant d'enregistrer quoi que ce soit, ou saisir une seconde vente.
     */
    LigneVenteDto ajouterLigne(Long idVente, LigneVenteDto ligne);

    /**
     * Attribue ou change le client d'une vente, tant qu'elle n'est ni annulee ni facturee.
     *
     * Le caissier ne sait pas toujours d'avance a qui il vend : le client se presente, ou se fait
     * connaitre au moment de payer.
     */
    VenteDto attribuerClient(Long idVente, Long idClient);

    /**
     * Cree la vente qui sert une commande client, et passe celle-ci en livree.
     *
     * Une commande client est un engagement : elle ne touche pas au stock. C'est la vente qui la
     * sert qui sort la marchandise, et ce lien est le seul endroit ou l'on sait a qui la vente est
     * faite — la vente, seule, ne connait pas son client.
     */
    VenteDto servirCommandeClient(Long idCommandeClient);

    /**
     * Sert une partie seulement de la commande : ce que nomme la liste, et rien de plus.
     *
     * Une liste vide vaut « tout ce qui reste du ». L'etat de la commande est constate a partir
     * du reliquat, jamais declare : il reste quelque chose, elle est partiellement livree.
     */
    VenteDto servirCommandeClient(Long idCommandeClient, List<LigneReceptionDto> partiel);

    /**
     * Annule une vente et remet sa marchandise en magasin.
     *
     * La vente n'est pas effacee : une recette encaissee puis rendue doit rester lisible. Le
     * stock, lui, est rattrape par une entree portant le motif ANNULATION_VENTE — sans quoi
     * l'historique montrerait une entree indiscernable d'une livraison.
     */
    VenteDto annuler(Long idVente);

    /**
     * Corrige la quantite d'une ligne deja vendue, et rattrape le stock de la difference.
     *
     * Contrairement a une commande, une vente a deja sorti sa marchandise : on ne peut pas
     * reecrire la ligne et s'en tenir la. Augmenter sort le complement — et echoue si le magasin
     * ne l'a pas ; diminuer remet la difference.
     */
    LigneVenteDto modifierQuantite(Long idVente, Long idLigne, BigDecimal quantite);

    /** Retire une ligne d'une vente et remet sa quantite en magasin. */
    void retirerLigne(Long idVente, Long idLigne);

    void delete(Long id);
}
