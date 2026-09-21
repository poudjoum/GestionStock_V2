package com.jumpy.tech.gestionstock.gestiondestock.handlers;

import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class ErrorDto {
    private Integer httpCode;
    private ErrorCodes errorCode;
    private String message;
    // Meme piege que sur Utilisateur.roles : sans @Builder.Default, la liste construite par le
    // builder vaut null au lieu d'etre vide.
    @Builder.Default
    private List<String> errors=new ArrayList<>();
}
