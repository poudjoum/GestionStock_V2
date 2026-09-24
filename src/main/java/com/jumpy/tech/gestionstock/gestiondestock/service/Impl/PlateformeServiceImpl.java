package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.ServiceDeRafraichissement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CommerceDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ResumePlateformeDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.StatutAbonnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FactureRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.VenteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.PlateformeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PlateformeServiceImpl implements PlateformeService {

    private final EntrepriseRepository entrepriseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ArticleRepository articleRepository;
    private final VenteRepository venteRepository;
    private final FactureRepository factureRepository;
    private final ServiceDeRafraichissement rafraichissement;
    private final Cloisonnement cloisonnement;

    public PlateformeServiceImpl(EntrepriseRepository entrepriseRepository,
                                 UtilisateurRepository utilisateurRepository,
                                 ArticleRepository articleRepository,
                                 VenteRepository venteRepository,
                                 FactureRepository factureRepository,
                                 ServiceDeRafraichissement rafraichissement,
                                 Cloisonnement cloisonnement) {
        this.entrepriseRepository = entrepriseRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.articleRepository = articleRepository;
        this.venteRepository = venteRepository;
        this.factureRepository = factureRepository;
        this.rafraichissement = rafraichissement;
        this.cloisonnement = cloisonnement;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommerceDto> commerces() {
        exigerLEditeur();
        LocalDate aujourdhui = LocalDate.now();

        // Quatre requetes groupees, et non quatre par commerce : le tableau de bord doit couter la
        // meme chose avec trois clients qu'avec trois cents.
        Map<Long, Long> comptes = nombres(utilisateurRepository.comptesParEntreprise());
        Map<Long, Long> articles = nombres(articleRepository.articlesParEntreprise());
        Map<Long, Long> ventes = new HashMap<>();
        Map<Long, Instant> dernieres = new HashMap<>();
        for (Object[] ligne : venteRepository.ventesParEntreprise()) {
            Long id = (Long) ligne[0];
            ventes.put(id, ((Number) ligne[1]).longValue());
            dernieres.put(id, (Instant) ligne[2]);
        }
        Map<Long, BigDecimal> chiffres = new HashMap<>();
        for (Object[] ligne : factureRepository.chiffreFactureParEntreprise()) {
            chiffres.put((Long) ligne[0], (BigDecimal) ligne[1]);
        }

        return entrepriseRepository.findAll().stream()
                .map(entreprise -> new CommerceDto(
                        entreprise.getId(),
                        entreprise.getNom(),
                        entreprise.getAdresse() == null ? null : entreprise.getAdresse().getVille(),
                        entreprise.getTel(),
                        entreprise.getEmail_Entreprise(),
                        entreprise.getAbonnementEcheance(),
                        entreprise.isSuspendue(),
                        statut(entreprise, aujourdhui),
                        joursRestants(entreprise, aujourdhui),
                        comptes.getOrDefault(entreprise.getId(), 0L),
                        articles.getOrDefault(entreprise.getId(), 0L),
                        ventes.getOrDefault(entreprise.getId(), 0L),
                        chiffres.getOrDefault(entreprise.getId(), BigDecimal.ZERO),
                        dernieres.get(entreprise.getId())))
                // Le plus recemment inscrit d'abord : c'est celui dont on suit l'installation.
                .sorted(Comparator.comparing(CommerceDto::id).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumePlateformeDto resume() {
        List<CommerceDto> tous = commerces();
        return new ResumePlateformeDto(
                tous.size(),
                tous.stream().filter(c -> c.statut() == StatutAbonnement.ACTIF).count(),
                tous.stream().filter(c -> c.statut() == StatutAbonnement.ECHU).count(),
                tous.stream().filter(c -> c.statut() == StatutAbonnement.SUSPENDU).count(),
                // Ceux dont l'echeance tombe dans le mois : la relance se prepare avant, pas apres.
                tous.stream()
                        .filter(c -> c.statut() == StatutAbonnement.ACTIF)
                        .filter(c -> c.joursRestants() != null && c.joursRestants() <= 30)
                        .count(),
                tous.stream().mapToLong(CommerceDto::comptes).sum(),
                tous.stream().mapToLong(CommerceDto::ventes).sum(),
                tous.stream().map(CommerceDto::chiffreFacture)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Override
    @Transactional
    public CommerceDto suspendre(Long idEntreprise) {
        Entreprise entreprise = commerce(idEntreprise);
        entreprise.setSuspendue(true);
        entreprise.setSuspendueLe(Instant.now());
        entrepriseRepository.save(entreprise);

        // Fermer la porte sans reprendre les clefs deja distribuees ne fermerait rien : un poste
        // connecte continuerait a renouveler son jeton pendant trente jours.
        for (Utilisateur compte : utilisateurRepository.findAllByEntrepriseId(idEntreprise)) {
            rafraichissement.revoquerTout(compte.getId());
        }
        log.info("Commerce {} suspendu", entreprise.getNom());
        return relire(idEntreprise);
    }

    @Override
    @Transactional
    public CommerceDto reprendre(Long idEntreprise) {
        Entreprise entreprise = commerce(idEntreprise);
        entreprise.setSuspendue(false);
        entrepriseRepository.save(entreprise);
        log.info("Commerce {} repris", entreprise.getNom());
        return relire(idEntreprise);
    }

    @Override
    @Transactional
    public CommerceDto renouveler(Long idEntreprise) {
        Entreprise entreprise = commerce(idEntreprise);
        LocalDate aujourdhui = LocalDate.now();
        LocalDate depart = entreprise.getAbonnementEcheance() == null
                || entreprise.getAbonnementEcheance().isBefore(aujourdhui)
                ? aujourdhui
                : entreprise.getAbonnementEcheance();
        entreprise.setAbonnementEcheance(depart.plusYears(1));
        entrepriseRepository.save(entreprise);
        log.info("Commerce {} renouvelé jusqu'au {}", entreprise.getNom(),
                entreprise.getAbonnementEcheance());
        return relire(idEntreprise);
    }

    @Override
    @Transactional
    public CommerceDto fixerEcheance(Long idEntreprise, LocalDate echeance) {
        Entreprise entreprise = commerce(idEntreprise);
        entreprise.setAbonnementEcheance(echeance);
        entrepriseRepository.save(entreprise);
        return relire(idEntreprise);
    }

    /**
     * Le statut, deduit et jamais stocke.
     *
     * La suspension l'emporte sur l'echeance : elle est une decision, quand l'echeance n'est qu'une
     * date. Un commerce suspendu dont l'abonnement court encore reste suspendu.
     */
    private StatutAbonnement statut(Entreprise entreprise, LocalDate aujourdhui) {
        if (entreprise.isSuspendue()) {
            return StatutAbonnement.SUSPENDU;
        }
        return entreprise.accesOuvert(aujourdhui) ? StatutAbonnement.ACTIF : StatutAbonnement.ECHU;
    }

    private Integer joursRestants(Entreprise entreprise, LocalDate aujourdhui) {
        return entreprise.getAbonnementEcheance() == null
                ? null
                : (int) ChronoUnit.DAYS.between(aujourdhui, entreprise.getAbonnementEcheance());
    }

    private Map<Long, Long> nombres(List<Object[]> lignes) {
        Map<Long, Long> par = new HashMap<>();
        for (Object[] ligne : lignes) {
            par.put((Long) ligne[0], ((Number) ligne[1]).longValue());
        }
        return par;
    }

    private Entreprise commerce(Long idEntreprise) {
        exigerLEditeur();
        if (idEntreprise == null) {
            throw new EntityNotFoundException("Aucun commerce n'est désigné",
                    ErrorCodes.ENTREPRISE_NOT_FOUND);
        }
        return entrepriseRepository.findById(idEntreprise)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun commerce avec l'identifiant " + idEntreprise,
                        ErrorCodes.ENTREPRISE_NOT_FOUND));
    }

    /** Rend la ligne complete apres modification, compteurs compris. */
    private CommerceDto relire(Long idEntreprise) {
        return commerces().stream()
                .filter(c -> c.id().equals(idEntreprise))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun commerce avec l'identifiant " + idEntreprise,
                        ErrorCodes.ENTREPRISE_NOT_FOUND));
    }

    /**
     * Le cloisonnement protege chaque commerce des autres ; ce service, lui, regarde au-dessus de
     * tous. C'est le seul endroit du projet dans ce cas, et il se garde donc lui-meme.
     */
    private void exigerLEditeur() {
        if (!cloisonnement.estSuperAdmin()) {
            throw new AccessDeniedException("Seul un super-administrateur voit la plateforme");
        }
    }
}
