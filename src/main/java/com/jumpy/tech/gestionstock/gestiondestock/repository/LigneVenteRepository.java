package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneVente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneVenteRepository extends JpaRepository<LigneVente,Long> {

    /** Les lignes d'une vente, relues pour remettre la marchandise en magasin a l'annulation. */
    List<LigneVente> findAllByVenteId(Long idVente);
}
