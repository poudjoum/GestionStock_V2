package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneCmndeFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneCmndeFourRepository extends JpaRepository<LigneCmndeFournisseur,Long> {

    /** Les lignes d'une commande, relues a la livraison pour faire entrer la marchandise. */
    List<LigneCmndeFournisseur> findAllByCommandeFournisseurId(Long idCommandeFournisseur);
}
