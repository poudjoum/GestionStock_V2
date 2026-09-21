package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface FournisseurControllerApi {

    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/fournisseur/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    // @RequestBody manquait : le corps de la requete n'etait pas lie, et le service recevait un
    // fournisseur vide qu'il rejetait comme invalide.
    ResponseEntity<FournisseurDto> save(@RequestBody FournisseurDto dto);

    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/fournisseur/{idFour}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FournisseurDto> findById(@PathVariable Long idFour);

    // Meme motif que findById : cette route etait inatteignable.
    @GetMapping(value = APP_ROOT+"/fournisseur/nom/{nomFournisseur}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FournisseurDto> findFournisseurByNom(@PathVariable String nomFournisseur);

    @GetMapping(value = APP_ROOT+"/fournisseur/all",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<FournisseurDto>> findAll();

    /**
     * Liste paginee et filtrable : `?q=ciment&page=0&size=20&sort=nom,asc`.
     *
     * `q` porte sur le nom, le prenom, le courriel et le numero de telephone.
     */
    @GetMapping(value = APP_ROOT+"/fournisseur",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Page<FournisseurDto>> findAll(@RequestParam(required = false) String q, Pageable pageable);

    @DeleteMapping(value = APP_ROOT+"/fournisseur/delete/{idFour}")
    ResponseEntity<FournisseurDto> delete(@PathVariable Long idFour);
}
