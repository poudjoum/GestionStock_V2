package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FactureRepository extends JpaRepository<Facture, Long> {

    Optional<Facture> findByNumero(String numero);

    Optional<Facture> findByVenteId(Long idVente);

    boolean existsByVenteId(Long idVente);

    /**
     * Le prochain numero, pris dans la sequence de la base.
     *
     * Un compteur calcule en Java — « le plus grand numero plus un » — donnerait le meme numero a
     * deux factures emises en meme temps. La sequence, elle, ne rend jamais deux fois la meme
     * valeur, meme sous plusieurs instances de l'application.
     */
    @Query(value = "select nextval('facture_numero_seq')", nativeQuery = true)
    Long prochainNumero();
}
