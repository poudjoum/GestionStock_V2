package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Lot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByArticleIdAndNumeroIgnoreCase(Long idArticle, String numero);

    List<Lot> findAllByArticleIdOrderByDatePeremptionAscIdAsc(Long idArticle);

    /** Les lots d'une entreprise qui ont une date : ceux dont on surveille la peremption. */
    List<Lot> findAllByIdEntrepriseAndDatePeremptionNotNull(Long idEntreprise);
}
