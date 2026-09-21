package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.JetonRafraichissement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface JetonRafraichissementRepository extends JpaRepository<JetonRafraichissement, Long> {

    Optional<JetonRafraichissement> findByJeton(String jeton);

    /**
     * Revoque d'un coup tout ce qui reste ouvert pour un compte.
     *
     * Appele quand on ferme un acces ou qu'on change un mot de passe : sans cela, le jeton de
     * rafraichissement continuerait a delivrer des jetons d'acces a qui l'a copie, et fermer le
     * compte n'aurait ferme que la porte d'entree.
     */
    @Modifying
    @Query("update JetonRafraichissement j "
            + "set j.revoqueLe = :maintenant, "
            + "    j.motifRevocation = com.jumpy.tech.gestionstock.gestiondestock.entities.MotifRevocation.SECURITE "
            + "where j.utilisateur.id = :idUtilisateur and j.revoqueLe is null")
    int revoquerTousPourUtilisateur(@Param("idUtilisateur") Long idUtilisateur,
                                    @Param("maintenant") Instant maintenant);
}
