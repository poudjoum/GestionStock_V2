package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.SoldePointsMagasin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SoldePointsMagasinRepository extends JpaRepository<SoldePointsMagasin, Long> {

    Optional<SoldePointsMagasin> findByClientIdAndEntrepriseId(Long clientId, Long entrepriseId);

    List<SoldePointsMagasin> findAllByClientId(Long clientId);

    @Query("select coalesce(sum(s.soldePoints), 0) from SoldePointsMagasin s where s.client.id = :clientId")
    int totalPointsDuClient(Long clientId);
}
