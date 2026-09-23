package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.InscriptionEntrepriseDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface EntrepriseControllerApi {
    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/entreprise/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EntrepriseDto> save(@RequestBody EntrepriseDto dto);
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    /**
     * Inscrit une entreprise et son premier administrateur d'un seul geste.
     *
     * Reserve au super-administrateur, sauf sur une installation qui ne compte encore aucune
     * entreprise : il faut bien creer la premiere, et personne ne peut alors l'autoriser.
     */
    @PostMapping(value = APP_ROOT+"/entreprises/inscription",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EntrepriseDto> inscrire(@RequestBody InscriptionEntrepriseDto inscription);

    /**
     * L'entreprise pour laquelle travaille le compte connecte.
     *
     * Pendant du `/users/moi` : elle ne se demande pas par identifiant, elle se deduit du jeton.
     * C'est ce qu'il faut pour l'en-tete d'un ticket de caisse ou d'une facture — le nom de la
     * maison, son adresse, son registre de commerce — et le caissier qui les imprime n'a aucune
     * raison de pouvoir lire les autres entreprises pour autant.
     */
    @GetMapping(value = APP_ROOT+"/entreprises/mienne",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EntrepriseDto> mienne();

    @GetMapping(value = APP_ROOT+"/entreprise/{idEntreprise}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EntrepriseDto> findById(@PathVariable Long idEntreprise);
    @GetMapping(value = APP_ROOT+"/entreprises/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<EntrepriseDto>> findAll();
    @DeleteMapping(value = APP_ROOT+"/entreprise/delete/{idEntreprise}")
    ResponseEntity delete(@PathVariable Long idEntreprise);
}
