package com.jumpy.tech.gestionstock.gestiondestock.dto;

import java.util.List;

/**
 * Ce que l'import a fait, ou ferait, ligne par ligne.
 *
 * Le meme rapport sert a la simulation et a l'ecriture : c'est la seule facon de garantir que ce
 * que le commercant a vu avant de valider est ce qui est entre. Deux chemins distincts pour
 * « montrer » et pour « ecrire » finissent toujours par diverger, et l'ecart se decouvre apres
 * coup, sur un catalogue a moitie faux.
 *
 * @param simulation vrai si rien n'a ete ecrit — le rapport est alors une promesse, pas un constat
 * @param lues       lignes trouvees dans le fichier, en-tete exclu
 * @param creees     articles qui n'existaient pas
 * @param modifiees  articles deja presents, reconnus par leur code, dont les valeurs sont reprises
 * @param refusees   lignes que rien ne permet d'ecrire, chacune avec sa raison
 */
public record RapportImportDto(
        boolean simulation,
        int lues,
        int creees,
        int modifiees,
        List<LigneRefuseeDto> refusees) {

    /**
     * Une ligne que l'import laisse de cote.
     *
     * Le numero est celui du tableur, en-tete compris : c'est celui que le commercant lit dans la
     * marge de son fichier. Renvoyer un index a partir de zero l'obligerait a compter.
     *
     * @param ligne  numero de ligne tel qu'il s'affiche dans le tableur
     * @param code   le code lu, quand il y en a un — c'est par lui qu'on retrouve la ligne
     * @param raison ce qui manque ou ce qui ne va pas, dit en francais
     */
    public record LigneRefuseeDto(int ligne, String code, String raison) {
    }

    /** Rien n'a ete ecrit, et rien ne le sera : le fichier ne portait aucune ligne. */
    public static RapportImportDto vide(boolean simulation) {
        return new RapportImportDto(simulation, 0, 0, 0, List.of());
    }

    /** Vrai quand tout passe : c'est la condition pour proposer la validation sans reserve. */
    public boolean sansRefus() {
        return refusees.isEmpty();
    }
}
