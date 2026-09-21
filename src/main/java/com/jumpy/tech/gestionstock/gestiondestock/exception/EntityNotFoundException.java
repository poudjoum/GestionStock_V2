package com.jumpy.tech.gestionstock.gestiondestock.exception;

import lombok.Getter;

// @Data sur une exception generait un equals/hashCode qui ignorait la superclasse et un toString
// concurrent de celui de Throwable ; seul le lecteur du code d'erreur est utile ici.
@Getter
public class EntityNotFoundException extends RuntimeException {

    private ErrorCodes errorCode;

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public EntityNotFoundException(String message, Throwable cause, ErrorCodes errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    // C'est le constructeur qu'emploient tous les services, et son corps etait vide : ni
    // `super(message)`, ni l'affectation du code. Le client recevait un 404 au message nul et au
    // code d'erreur nul, quelle que soit l'entite manquante.
    public EntityNotFoundException(String message, ErrorCodes errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
