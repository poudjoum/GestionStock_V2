package com.jumpy.tech.gestionstock.gestiondestock.entities;

/**
 * L'unite dans laquelle le stock d'un article est tenu.
 *
 * Toutes les quantites de stock s'expriment dans cette unite ; un carton, un sac ou une boite
 * n'en sont que des multiples (voir Conditionnement). Un comprime de pharmacie est une PIECE : la
 * plaquette et la boite sont ses conditionnements.
 */
public enum UniteMesure {
    PIECE("pièce", false),
    KG("kg", true),
    LITRE("L", true),
    METRE("m", true),
    M2("m²", true),
    M3("m³", true);

    private final String symbole;
    private final boolean fractionnable;

    UniteMesure(String symbole, boolean fractionnable) {
        this.symbole = symbole;
        this.fractionnable = fractionnable;
    }

    public String getSymbole() {
        return symbole;
    }

    /**
     * Se vend-on au detail de cette unite ? 1,250 kg de riz, oui ; une demi-bouteille, non.
     */
    public boolean isFractionnable() {
        return fractionnable;
    }
}
