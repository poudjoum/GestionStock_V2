package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CodeBarres;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeCodeBarres;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un code-barres, et ce qu'il designe : l'unite de base si `idConditionnement` est nul. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeBarresDto {
    private Long id;
    private String code;
    /** Facultatif a la creation : deduit du code quand il n'est pas donne. */
    private TypeCodeBarres type;
    private Long idArticle;
    private Long idConditionnement;

    public static CodeBarresDto fromEntity(CodeBarres code) {
        if (code == null) {
            return null;
        }
        return CodeBarresDto.builder()
                .id(code.getId())
                .code(code.getCode())
                .type(code.getType())
                .idArticle(code.getArticle() == null ? null : code.getArticle().getId())
                .idConditionnement(code.getConditionnement() == null ? null : code.getConditionnement().getId())
                .build();
    }
}
