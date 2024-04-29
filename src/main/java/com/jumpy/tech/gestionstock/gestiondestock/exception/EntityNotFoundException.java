package com.jumpy.tech.gestionstock.gestiondestock.exception;

import lombok.Data;
import lombok.Getter;

@Data
public class EntityNotFoundException extends RuntimeException{

    @Getter
    private ErrorCodes errorCode;

    public EntityNotFoundException(String message,Throwable cause){
        super(message,cause);
    }
    public EntityNotFoundException(String message){
        super(message);
    }
    public EntityNotFoundException(String message,Throwable cause,ErrorCodes codeError){
        super(message,cause);
        this.errorCode=codeError;
    }
}
