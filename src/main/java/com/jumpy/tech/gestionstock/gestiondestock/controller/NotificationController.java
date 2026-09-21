package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.controller.api.NotificationApi;
import com.jumpy.tech.gestionstock.gestiondestock.dto.NotificationDto;
import com.jumpy.tech.gestionstock.gestiondestock.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public ResponseEntity<Page<NotificationDto>> mesNotifications(boolean nonLues, Pageable pageable) {
        return ResponseEntity.ok(notificationService.mesNotifications(nonLues, pageable));
    }

    @Override
    public ResponseEntity<Long> compteNonLues() {
        return ResponseEntity.ok(notificationService.compteNonLues());
    }

    @Override
    public ResponseEntity<NotificationDto> marquerLue(Long idNotification) {
        return ResponseEntity.ok(notificationService.marquerLue(idNotification));
    }

    @Override
    public ResponseEntity<Integer> marquerToutesLues() {
        return ResponseEntity.ok(notificationService.marquerToutesLues());
    }
}
