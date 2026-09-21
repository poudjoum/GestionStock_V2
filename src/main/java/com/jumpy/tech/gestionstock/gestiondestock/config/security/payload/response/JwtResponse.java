package com.jumpy.tech.gestionstock.gestiondestock.config.security.payload.response;
import lombok.Data;

import java.util.List;
@Data
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    /**
     * Le jeton qui permet d'en redemander un autre.
     *
     * Il ne porte aucun droit et ne s'envoie jamais dans l'en-tete `Authorization` : il ne sert
     * qu'a la route de rafraichissement. C'est lui, et non le jeton d'acces, qu'un client garde
     * entre deux sessions.
     */
    private String refreshToken;
    private Long id;
    private String username;
    private String email;
    private List<String> roles;

    public JwtResponse(String accessToken, String refreshToken, Long id, String username, String email,
                       List<String> roles) {
        this.token = accessToken;
        this.refreshToken = refreshToken;
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
    }

    public String getAccessToken() {
        return token;
    }

    public void setAccessToken(String accessToken) {
        this.token = accessToken;
    }

    public String getTokenType() {
        return type;
    }

    public void setTokenType(String tokenType) {
        this.type = tokenType;
    }
}


