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

    /**
     * La vente deja enregistree sous cette reference, s'il y en a une.
     *
     * C'est ce qui rend un envoi rejouable : un poste qui perd le reseau au milieu d'un envoi
     * reessaie sans savoir si le premier est passe.
     */
    Optional<Vente> findVenteByReferenceClient(String referenceClient);

    /**
     * Le nombre de ventes et la date de la derniere, par entreprise.
     *
     * Les ventes annulees sont exclues du compte : une vente rendue n'est pas une vente. Sa date
     * compte en revanche comme signe de vie — c'est bien quelqu'un qui a touche l'application ce
     * jour-la.
     */
    @org.springframework.data.jpa.repository.Query(
            "select v.idEntreprise, count(v), max(v.datevente) from Vente v "
            + "where v.idEntreprise is not null and v.annulee = false group by v.idEntreprise")
    java.util.List<Object[]> ventesParEntreprise();
}
