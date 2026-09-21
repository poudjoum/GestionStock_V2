package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FournisseurRepository extends JpaRepository<Fournisseur,Long> {
    Optional<Fournisseur> findFournisseurByNom(String nomFournisseur);

    java.util.List<Fournisseur> findAllByIdEntreprise(Long idEntreprise);

    org.springframework.data.domain.Page<Fournisseur> findAllByIdEntreprise(
            Long idEntreprise, org.springframework.data.domain.Pageable pageable);

    Optional<Fournisseur> findFournisseurByNomAndIdEntreprise(String nomFournisseur, Long idEntreprise);
}
