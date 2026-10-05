package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CodeBarres;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CodeBarresRepository extends JpaRepository<CodeBarres, Long> {

    // L'entreprise se compare a l'identique : deux commerces vendent le meme produit, donc le
    // meme EAN, et chacun doit retrouver le sien.
    Optional<CodeBarres> findByCodeAndIdEntreprise(String code, Long idEntreprise);

    Optional<CodeBarres> findByCodeAndIdEntrepriseIsNull(String code);

    boolean existsByCodeAndIdEntreprise(String code, Long idEntreprise);

    List<CodeBarres> findAllByArticleIdOrderByIdAsc(Long idArticle);

    List<CodeBarres> findAllByArticleIdInOrderByIdAsc(Collection<Long> idsArticles);

    List<CodeBarres> findAllByConditionnementId(Long idConditionnement);
}
