package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EtatDeCaisseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ReglementDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ModeReglement;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ReglementRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.CaisseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CaisseServiceImpl implements CaisseService {

    private final ReglementRepository reglementRepository;
    private final Cloisonnement cloisonnement;

    /**
     * Le fuseau dans lequel se lisent les journees.
     *
     * L'application tourne en UTC dans son conteneur : sans ce reglage, « la caisse du 21 » irait
     * de 01h00 a 01h00 en heure locale, et les encaissements du soir compteraient pour le
     * lendemain.
     */
    private final ZoneId fuseau;

    public CaisseServiceImpl(ReglementRepository reglementRepository, Cloisonnement cloisonnement,
                             @Value("${app.fuseauHoraire:Africa/Douala}") String fuseauHoraire) {
        this.reglementRepository = reglementRepository;
        this.cloisonnement = cloisonnement;
        this.fuseau = ZoneId.of(fuseauHoraire);
    }

    @Override
    public EtatDeCaisseDto etat(LocalDate debut, LocalDate fin) {
        LocalDate du = debut == null ? aujourdhui() : debut;
        LocalDate au = fin == null ? du : fin;
        verifierPeriode(du, au);

        List<Object[]> lignes = cloisonnement.filtre()
                ? reglementRepository.totauxParModePourEntreprise(
                        debutDeJournee(du), finDeJournee(au), cloisonnement.entrepriseCourante())
                : reglementRepository.totauxParMode(debutDeJournee(du), finDeJournee(au));

        List<EtatDeCaisseDto.TotalParModeDto> parMode = lignes.stream()
                .map(ligne -> EtatDeCaisseDto.TotalParModeDto.builder()
                        .mode((ModeReglement) ligne[0])
                        .total((BigDecimal) ligne[1])
                        .nombre((Long) ligne[2])
                        .build())
                .collect(Collectors.toList());

        // Le total est la somme des lignes affichees, et non une requete de plus : ainsi il
        // correspond toujours a ce que l'on a sous les yeux.
        BigDecimal total = parMode.stream()
                .map(EtatDeCaisseDto.TotalParModeDto::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long nombre = parMode.stream().mapToLong(EtatDeCaisseDto.TotalParModeDto::getNombre).sum();

        return EtatDeCaisseDto.builder()
                .debut(du)
                .fin(au)
                .total(total)
                .nombreReglements(nombre)
                .parMode(parMode)
                .build();
    }

    @Override
    public Page<ReglementDto> reglements(LocalDate debut, LocalDate fin, Pageable pageable) {
        LocalDate du = debut == null ? aujourdhui() : debut;
        LocalDate au = fin == null ? du : fin;
        verifierPeriode(du, au);

        return (cloisonnement.filtre()
                ? reglementRepository.detailPourEntreprise(
                        debutDeJournee(du), finDeJournee(au), cloisonnement.entrepriseCourante(), pageable)
                : reglementRepository.detail(debutDeJournee(du), finDeJournee(au), pageable))
                .map(ReglementDto::fromEntity);
    }

    private void verifierPeriode(LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut)) {
            throw new InvalidEntityException(
                    "La fin de période précède son début : " + debut + " à " + fin,
                    ErrorCodes.VENTE_NOT_VALID);
        }
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(fuseau);
    }

    private Instant debutDeJournee(LocalDate jour) {
        return jour.atStartOfDay(fuseau).toInstant();
    }

    /**
     * Le lendemain a zero heure : la borne haute est exclue, ce qui evite d'avoir a choisir entre
     * 23:59:59 et 23:59:59.999 — et de perdre au passage un encaissement de la derniere seconde.
     */
    private Instant finDeJournee(LocalDate jour) {
        return jour.plusDays(1).atStartOfDay(fuseau).toInstant();
    }
}
