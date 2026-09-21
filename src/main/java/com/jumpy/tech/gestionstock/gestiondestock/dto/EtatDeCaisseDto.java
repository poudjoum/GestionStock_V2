package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ce qui est entre en caisse sur une periode, et par quel moyen.
 *
 * Le detail par mode n'est pas une curiosite : c'est ce qui permet de verifier. Les especes se
 * comptent dans le tiroir, le mobile money se confronte au releve de l'operateur, les cheques se
 * comptent en nombre. Un total global, seul, ne se controle contre rien.
 */
@Builder
@Data
public class EtatDeCaisseDto {

    private LocalDate debut;
    private LocalDate fin;

    /** Total encaisse sur la periode, tous moyens confondus. */
    private BigDecimal total;

    private long nombreReglements;

    private List<TotalParModeDto> parMode;

    @Builder
    @Data
    public static class TotalParModeDto {
        private ModeReglement mode;
        private BigDecimal total;
        private long nombre;
    }
}
