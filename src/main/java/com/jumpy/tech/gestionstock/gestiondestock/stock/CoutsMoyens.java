package com.jumpy.tech.gestionstock.gestiondestock.stock;

import com.jumpy.tech.gestionstock.gestiondestock.repository.LigneCmndeFourRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le cout d'achat moyen d'un article, par unite de base : tout ce qui a ete achete et est entre,
 * divise par tout ce qui est entre.
 *
 * Moyen et non « dernier prix connu » : le dernier achat peut etre une petite quantite a un prix
 * exceptionnel. Nul pour un article jamais recu par une commande — approvisionne a la main.
 */
@Component
public class CoutsMoyens {

    private static final int DECIMALES = 4;

    private final LigneCmndeFourRepository ligneCmndeFourRepository;

    public CoutsMoyens(LigneCmndeFourRepository ligneCmndeFourRepository) {
        this.ligneCmndeFourRepository = ligneCmndeFourRepository;
    }

    public Map<Long, BigDecimal> de(Collection<Long> idsArticles) {
        Map<Long, BigDecimal> couts = new HashMap<>();
        if (idsArticles.isEmpty()) {
            return couts;
        }
        for (Object[] ligne : ligneCmndeFourRepository.coutsAchetes(idsArticles)) {
            BigDecimal montant = (BigDecimal) ligne[1];
            BigDecimal quantite = (BigDecimal) ligne[2];
            if (quantite != null && quantite.signum() > 0) {
                couts.put((Long) ligne[0], montant.divide(quantite, DECIMALES, RoundingMode.HALF_UP));
            }
        }
        return couts;
    }

    /** Le cout d'un article, ou nul s'il n'a jamais ete achete par une commande. */
    public BigDecimal de(Long idArticle) {
        return idArticle == null ? null : de(List.of(idArticle)).get(idArticle);
    }
}
