package com.jumpy.tech.gestionstock.gestiondestock.entities;

import com.jumpy.tech.gestionstock.gestiondestock.dto.RoleDto;

public enum ERole {
    /**
     * Au-dessus des entreprises : celui qui les cree et qui peut regarder au-dela de l'une
     * d'elles. C'est l'editeur, pas le gerant — ROLE_ADMIN administre sa seule entreprise.
     */
    ROLE_SUPER_ADMIN,
    ROLE_USER,
    ROLE_CAISSIER,
    ROLE_ADMIN,
    ROLE_COMPTABLE,
    ROLE_MANAGER,
    ROLE_MAGASINIER
}
