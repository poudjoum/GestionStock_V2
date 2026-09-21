package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CanalEnvoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Envoi;
import com.jumpy.tech.gestionstock.gestiondestock.entities.EtatEnvoi;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EnvoiRepository extends JpaRepository<Envoi, Long> {

    /**
     * Ce qui attend et dont l'heure est venue, du plus ancien au plus recent.
     *
     * Par paquets : une file qui a grossi pendant une panne de SMTP se viderait sinon d'un seul
     * bloc, en tenant une transaction ouverte le temps de plusieurs centaines d'envois.
     */
    List<Envoi> findAllByCanalAndEtatAndProchaineTentativeLessThanEqualOrderByIdAsc(
            CanalEnvoi canal, EtatEnvoi etat, Instant maintenant, Pageable paquet);

    long countByEtat(EtatEnvoi etat);
}
