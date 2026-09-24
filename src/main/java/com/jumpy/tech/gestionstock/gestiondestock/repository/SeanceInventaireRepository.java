package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.SeanceInventaire;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutSeanceInventaire;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SeanceInventaireRepository extends JpaRepository<SeanceInventaire, Long> {

    // `findBy...IdEntreprise(null)` rend bien les lignes ou la colonne est nulle : c'est le cas
    // tant que le rattachement d'une donnee a son entreprise n'a pas ete fait partout.
    Optional<SeanceInventaire> findByStatutAndIdEntreprise(StatutSeanceInventaire statut, Long idEntreprise);

    Page<SeanceInventaire> findAllByIdEntreprise(Long idEntreprise, Pageable pageable);

    /**
     * Le prochain rang, pris dans la sequence de la base.
     *
     * Un compteur calcule en Java — « la plus grande reference plus un » — donnerait la meme a
     * deux seances ouvertes en meme temps. La sequence, elle, ne rend jamais deux fois la meme
     * valeur, meme sous plusieurs instances de l'application.
     */
    @Query(value = "select nextval('inventaire_reference_seq')", nativeQuery = true)
    Long prochaineReference();
}
