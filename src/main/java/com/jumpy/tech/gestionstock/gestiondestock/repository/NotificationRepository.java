package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByDestinataireIdOrderByIdDesc(Long idDestinataire, Pageable pageable);

    Page<Notification> findAllByDestinataireIdAndLuLeIsNullOrderByIdDesc(Long idDestinataire,
                                                                        Pageable pageable);

    long countByDestinataireIdAndLuLeIsNull(Long idDestinataire);

    /**
     * Une alerte de cette nature attend-elle deja d'etre lue dans cette entreprise ?
     *
     * Une seule question pour toute l'entreprise, et non une par destinataire : un article sous
     * son seuil est un fait unique, et le repeter a chaque vente ferait de la boite aux lettres
     * un bruit qu'on finit par ignorer.
     */
    boolean existsByIdEntrepriseAndCleAndLuLeIsNull(Long idEntreprise, String cle);

    /** Tout marquer lu d'un coup : le geste ordinaire quand on a pris connaissance de la liste. */
    @Modifying
    @Query("update Notification n set n.luLe = :quand "
            + "where n.destinataire.id = :idDestinataire and n.luLe is null")
    int marquerToutesLues(@Param("idDestinataire") Long idDestinataire,
                          @Param("quand") Instant quand);
}
