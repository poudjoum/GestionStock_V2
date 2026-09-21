package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Notification;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeNotification;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** Une notification telle que le front la lit. Le destinataire n'y figure pas : c'est le lecteur. */
@Builder
@Data
public class NotificationDto {

    private Long id;
    private TypeNotification type;
    private String titre;
    private String corps;
    /** Un chemin dans l'application, ou nul. */
    private String lien;
    private boolean lue;
    private Instant luLe;
    private Instant creeLe;

    public static NotificationDto fromEntity(Notification notification) {
        if (notification == null) {
            return null;
        }
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .titre(notification.getTitre())
                .corps(notification.getCorps())
                .lien(notification.getLien())
                .lue(notification.estLue())
                .luLe(notification.getLuLe())
                .creeLe(notification.getCreationDate())
                .build();
    }
}
