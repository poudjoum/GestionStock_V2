package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.BonDAchat;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutBonDAchat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BonDAchatRepository extends JpaRepository<BonDAchat, Long> {

    Optional<BonDAchat> findByCodeBon(String codeBon);

    /** Le bon, verrouille : deux caisses qui l'encaissent en meme temps ne le consomment qu'une fois. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BonDAchat b where b.codeBon = :code")
    Optional<BonDAchat> verrouillerParCode(@Param("code") String code);

    List<BonDAchat> findAllByClientIdOrderByDateEmissionDesc(Long clientId);

    List<BonDAchat> findAllByClientIdAndStatutOrderByDateEmissionDesc(Long clientId, StatutBonDAchat statut);
}
