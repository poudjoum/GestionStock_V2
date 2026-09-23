package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

/**
 * L'import du catalogue, dans ses propres routes.
 *
 * A part de {@code ArticleControllerApi} parce que ce n'est pas la meme chose : on ne cree pas un
 * article, on remplit un magasin. Le corps est un fichier et non du JSON, la reponse est un
 * rapport et non une entite, et les droits n'ont pas de raison d'etre les memes.
 */
public interface ImportArticleControllerApi {

    /**
     * Lit un classeur et rend ce qui entrerait, ou ce qui est entre.
     *
     * {@code simulation} vaut vrai par defaut, et ce n'est pas un detail : un appel maladroit
     * montre sans ecrire. Ecrire se demande.
     */
    @Tag(name = "Post", description = "Post Methods of Gestion de Stock APIs")
    @Operation(summary = "Importer un catalogue d'articles depuis un classeur Excel",
            description = "Simule par defaut. Les articles sont reconnus par leur code : un "
                    + "fichier corrige et rejoue met a jour au lieu de creer des doublons.")
    @PostMapping(value = APP_ROOT + "/articles/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RapportImportDto> importer(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "simulation", defaultValue = "true") boolean simulation);

    /** Le classeur vierge, avec ses colonnes et une ligne d'exemple. */
    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @Operation(summary = "Telecharger le modele de fichier a remplir")
    @GetMapping(value = APP_ROOT + "/articles/import/modele")
    ResponseEntity<byte[]> modele();
}
