package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VenteRepository extends JpaRepository<Vente,Long> {

    Optional<Vente>findVenteByCode(String codeVente);

    List<Vente> findAllByIdEntreprise(Long idEntreprise);

    Page<Vente> findAllByIdEntreprise(Long idEntreprise, Pageable pageable);

    Optional<Vente> findVenteByCodeAndIdEntreprise(String codeVente, Long idEntreprise);
}
