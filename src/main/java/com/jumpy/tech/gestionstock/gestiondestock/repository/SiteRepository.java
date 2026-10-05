package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findAllByIdEntrepriseOrderByPrincipalDescNomAsc(Long idEntreprise);

    Optional<Site> findByIdEntrepriseAndPrincipalTrue(Long idEntreprise);

    long countByIdEntrepriseAndActifTrue(Long idEntreprise);
}
