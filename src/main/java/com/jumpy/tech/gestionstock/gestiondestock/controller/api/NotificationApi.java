package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.NotificationDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

/**
 * Ce qu'un compte a recu.
 *
 * Toutes ces routes parlent du compte connecte, et de lui seul : il n'y a pas d'identifiant
 * d'utilisateur dans les chemins. Demander les notifications d'un autre n'est pas interdit, c'est
 * impossible.
 */
public interface NotificationApi {

    /**
     * Mes notifications, les plus recentes d'abord.
     *
     * `?nonLues=true` pour la liste deroulante de la cloche, qui n'a que faire de l'historique.
     */
    @Tag(name = "Get", description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT + "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Page<NotificationDto>> mesNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean nonLues,
            Pageable pageable);

    /** Le nombre a afficher sur la cloche. Une seule valeur, pour ne pas charger la liste. */
    @GetMapping(value = APP_ROOT + "/notifications/non-lues", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Long> compteNonLues();

    @PatchMapping(value = APP_ROOT + "/notifications/{idNotification}/lue",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<NotificationDto> marquerLue(@PathVariable Long idNotification);

    /** Tout marquer lu, et rendre combien l'ont ete. */
    @PatchMapping(value = APP_ROOT + "/notifications/lues", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Integer> marquerToutesLues();
}
