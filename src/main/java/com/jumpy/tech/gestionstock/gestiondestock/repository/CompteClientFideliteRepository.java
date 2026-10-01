package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CompteClientFidelite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompteClientFideliteRepository extends JpaRepository<CompteClientFidelite, Long> {
    Optional<CompteClientFidelite> findByTelephone(String telephone);
    boolean existsByTelephone(String telephone);

    /**
     * Le compte, verrouille jusqu'a la fin de la transaction.
     *
     * Tout ce qui touche aux points d'un client passe par ce verrou : deux conversions lancees en
     * meme temps lisaient le meme solde, le debitaient chacune, et l'une ecrasait l'autre — deux
     * bons pour le prix d'un. Verrouiller le compte plutot que le solde couvre aussi le premier
     * ticket dans un magasin, quand le solde n'existe pas encore.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CompteClientFidelite c where c.id = :id")
    Optional<CompteClientFidelite> verrouiller(@Param("id") Long id);
}
