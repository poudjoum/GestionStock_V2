package com.jumpy.tech.gestionstock.gestiondestock.handlers;

import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
@RestControllerAdvice
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorDto> handleException(EntityNotFoundException exception, WebRequest webRequest){

        final HttpStatus notFound=HttpStatus.NOT_FOUND;
        final ErrorDto errorDto=ErrorDto.builder()
                .errorCode(exception.getErrorCode())
                .httpCode(notFound.value())
                .message(exception.getMessage())
                .build();

        return new ResponseEntity<>(errorDto,notFound);
    }
    /**
     * Violation d'une contrainte de la base — aujourd'hui l'unicite de l'identifiant ou du
     * courriel d'un compte.
     *
     * Sans ce traitement, la contrainte ajoutee par V3 remonterait en erreur 500 avec une trace
     * PostgreSQL : le client ne saurait pas que le nom est simplement deja pris. 409 dit
     * exactement cela — la demande est recevable, l'etat actuel s'y oppose.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> handleException(DataIntegrityViolationException ex, WebRequest webRequest) {
        final HttpStatus conflit = HttpStatus.CONFLICT;
        log.warn("Violation d'une contrainte d'integrite : {}", ex.getMostSpecificCause().getMessage());
        final ErrorDto errorDto = ErrorDto.builder()
                .httpCode(conflit.value())
                .message("Cette donnee existe deja : identifiant ou adresse de courriel en double")
                // Le detail de la contrainte reste dans le journal du serveur : un nom d'index et
                // un fragment de SQL renseignent surtout celui qui cherche a deviner le schema.
                .errors(List.of())
                .build();
        return new ResponseEntity<>(errorDto, conflit);
    }

    @ExceptionHandler(InvalidEntityException.class)
    public ResponseEntity<ErrorDto> handleException(InvalidEntityException ex,WebRequest webRequest){
        final HttpStatus badRequest=HttpStatus.BAD_REQUEST;

        final ErrorDto errorDto=ErrorDto.builder()
                .errorCode(ex.getErrorCode())
                .httpCode(badRequest.value())
                .message(ex.getMessage())
                .errors(ex.getErrors())
                .build();
        return new ResponseEntity<>(errorDto,badRequest);
    }
}
