package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CommandeFourDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneCmndeFournisseurDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.LigneReceptionDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatCommande;

import java.math.BigDecimal;
import java.util.List;

public interface CommandeFourService {
    CommandeFourDto save( CommandeFourDto dto);
    CommandeFourDto findById(Long id);
    CommandeFourDto findByCode(String code);
    List< CommandeFourDto> findAll();

    /**
     * Les commandes, paginees et filtrables par etat.
     *
     * C'est ce dont le quai a besoin : le magasinier qui decharge un camion cherche les commandes
     * qu'il peut recevoir, et non l'historique complet des achats. `findAll` les rendait toutes,
     * d'un bloc — tenable sur quelques dizaines de lignes, pas sur un telephone au bout de trois
     * ans d'exploitation.
     */
    org.springframework.data.domain.Page<CommandeFourDto> rechercher(
            List<EtatCommande> etats, String q, org.springframework.data.domain.Pageable pageable);

    /**
     * Fait avancer la commande dans son cycle de vie. Le passage en LIVREE fait entrer la
     * marchandise en magasin ; c'est la seule transition qui touche au stock.
     */
    CommandeFourDto mettreAJourEtat(Long id, EtatCommande etat);

    /**
     * Enregistre ce qui est reellement arrive, ligne par ligne.
     *
     * Une commande se soldait d'un coup : recevoir 6 unites sur 10 obligeait a mentir en declarant
     * tout livre, ou a ne rien enregistrer en attendant le reste. L'etat n'est pas declare mais
     * constate — tout est arrive, la commande est livree ; il manque quelque chose, elle est
     * partiellement livree.
     */
    CommandeFourDto recevoir(Long id, List<LigneReceptionDto> receptions);

    /**
     * Solde une commande partiellement livree dont le reste n'arrivera pas.
     *
     * Sans elle, une commande dont le fournisseur fait defaut restait PARTIELLEMENT_LIVREE
     * indefiniment : elle figurait parmi les commandes en cours, et le reliquat continuait de
     * paraitre attendu. La cloture ne touche pas au stock — rien n'est arrive, il n'y a rien a
     * enregistrer ; elle ne fait que cesser d'attendre.
     *
     * Le motif est exige : « on a clos » sans dire pourquoi ne sert a rien six mois plus tard.
     */
    CommandeFourDto cloturer(Long id, String motif);

    /**
     * Les lignes d'une commande.
     *
     * Elles ne figurent pas dans CommandeFourDto.fromEntity, qui ne remonte que l'en-tete : une
     * commande se lit souvent en liste, et charger les lignes de chacune pour les jeter aussitot
     * coute plus que de les demander quand on en a besoin.
     */
    List<LigneCmndeFournisseurDto> lignes(Long idCommande);

    /**
     * Les trois operations qui manquaient : une commande enregistree ne se corrigeait pas.
     *
     * Elles n'ecrivent aucun mouvement de stock, et n'en ont pas besoin : la marchandise n'entre
     * qu'a la livraison, qui relit les lignes telles qu'elles sont a ce moment-la. Modifier une
     * commande avant sa livraison n'a donc rien a rattraper — c'est le cycle de vie qui rend ces
     * operations simples.
     */
    LigneCmndeFournisseurDto ajouterLigne(Long idCommande, LigneCmndeFournisseurDto ligne);

    /**
     * Corrige la quantite, le prix d'achat, ou les deux. Un parametre nul ne change rien.
     *
     * Le prix est celui qui alimentera le cout moyen de l'article a la livraison : le laisser
     * definitif des la creation de la ligne obligeait a retirer la ligne pour corriger un tarif.
     */
    LigneCmndeFournisseurDto modifierLigne(Long idCommande, Long idLigne, BigDecimal quantite,
                                           BigDecimal prixUnitaire);

    default LigneCmndeFournisseurDto modifierQuantite(Long idCommande, Long idLigne, BigDecimal quantite) {
        return modifierLigne(idCommande, idLigne, quantite, null);
    }

    void retirerLigne(Long idCommande, Long idLigne);

    void delete(Long id);
}
