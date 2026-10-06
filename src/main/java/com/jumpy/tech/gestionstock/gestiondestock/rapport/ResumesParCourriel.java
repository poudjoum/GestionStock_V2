package com.jumpy.tech.gestionstock.gestiondestock.rapport;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Entreprise;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.promotion.Calendrier;
import com.jumpy.tech.gestionstock.gestiondestock.repository.EntrepriseRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Les resumes du matin : la veille chaque jour si le commerce le demande, la semaine ecoulee le
 * lundi. Aux administrateurs et gerants actifs qui ont une adresse.
 *
 * Les chiffres sont ceux des ecrans de rapports, calcules pour toute l'entreprise, tous sites
 * confondus. Chaque resume ne part qu'une fois : le registre `resume_envoye` le garantit meme si
 * le serveur redemarre au moment de l'envoi. Un commerce en erreur n'empeche pas les autres.
 */
@Component
@Slf4j
public class ResumesParCourriel {

    private static final List<ERole> DESTINATAIRES = List.of(ERole.ROLE_ADMIN, ERole.ROLE_MANAGER);

    private final EntrepriseRepository entrepriseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ResumeEnvoyeRepository resumeEnvoyeRepository;
    private final RapportVentesService rapportVentesService;
    private final RapportPertesService rapportPertesService;
    private final NotificationService notifications;
    private final Calendrier calendrier;
    private final String adressePublique;

    public ResumesParCourriel(EntrepriseRepository entrepriseRepository, UtilisateurRepository utilisateurRepository,
                              ResumeEnvoyeRepository resumeEnvoyeRepository, RapportVentesService rapportVentesService,
                              RapportPertesService rapportPertesService, NotificationService notifications,
                              Calendrier calendrier, @Value("${app.adressePublique:}") String adressePublique) {
        this.entrepriseRepository = entrepriseRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.resumeEnvoyeRepository = resumeEnvoyeRepository;
        this.rapportVentesService = rapportVentesService;
        this.rapportPertesService = rapportPertesService;
        this.notifications = notifications;
        this.calendrier = calendrier;
        this.adressePublique = adressePublique;
    }

    /** Chaque matin a 7 h, heure du magasin. `app.resumes.cron` le change, « - » l'arrete. */
    @Scheduled(cron = "${app.resumes.cron:0 0 7 * * *}", zone = "${app.fuseauHoraire:Africa/Douala}")
    public void chaqueMatin() {
        int partis = envoyer(calendrier.aujourdhui());
        if (partis > 0) {
            log.info("Resumes du matin : {} courriel(s) mis en file", partis);
        }
    }

    /** Les resumes dus ce jour-la ; rend le nombre de courriels mis en file. */
    public int envoyer(LocalDate aujourdhui) {
        int courriels = 0;
        for (Entreprise entreprise : entrepriseRepository.findAll()) {
            if (entreprise.isSuspendue()) {
                continue;
            }
            try {
                if (entreprise.isResumeQuotidien()) {
                    LocalDate veille = aujourdhui.minusDays(1);
                    courriels += envoyerUn(entreprise, ResumeEnvoye.Type.QUOTIDIEN, veille, veille);
                }
                if (entreprise.isResumeHebdo() && aujourdhui.getDayOfWeek() == DayOfWeek.MONDAY) {
                    courriels += envoyerUn(entreprise, ResumeEnvoye.Type.HEBDO, aujourdhui.minusDays(7), aujourdhui.minusDays(1));
                }
            } catch (RuntimeException e) {
                log.error("Resume de l'entreprise {} non envoye : {}", entreprise.getId(), e.getMessage(), e);
            }
        }
        return courriels;
    }

    private int envoyerUn(Entreprise entreprise, ResumeEnvoye.Type type, LocalDate debut, LocalDate fin) {
        if (resumeEnvoyeRepository.existsByIdEntrepriseAndTypeAndDebut(entreprise.getId(), type, debut)) {
            return 0;
        }
        List<String> adresses = utilisateurRepository.findDestinatairesActifs(entreprise.getId(), DESTINATAIRES).stream()
                .map(Utilisateur::getEmail)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        int partis = 0;
        if (!adresses.isEmpty()) {
            ResumeCourriel courriel = pourLEntreprise(entreprise, () -> {
                RapportVentesDto ventes = rapportVentesService.ventes(debut, fin, true);
                // Une journee sans vente ne merite pas un courriel : le magasin etait ferme.
                if (type == ResumeEnvoye.Type.QUOTIDIEN && ventes.getCourant().getTickets() == 0) {
                    return null;
                }
                RapportPertesDto pertes = rapportPertesService.pertes(debut, fin, true);
                return ResumeCourriel.ecrire(new ResumeCourriel.Contenu(entreprise.getNom(), type, debut, fin,
                        ventes, pertes, adressePublique));
            });
            if (courriel != null) {
                for (String adresse : adresses) {
                    notifications.mettreEnFile(adresse, courriel.sujet(), courriel.texte(), courriel.html(), entreprise.getId());
                    partis++;
                }
            }
        }
        // Note meme sans destinataire ni vente : ce jour-la est regle, on ne le recalcule pas.
        ResumeEnvoye trace = new ResumeEnvoye();
        trace.setIdEntreprise(entreprise.getId());
        trace.setType(type);
        trace.setDebut(debut);
        trace.setEnvoyeLe(Instant.now());
        resumeEnvoyeRepository.save(trace);
        return partis;
    }

    /**
     * Les rapports se calculent pour un utilisateur connecte, cloisonne a son entreprise. Le matin,
     * personne ne l'est : on se met a la place d'un administrateur de ce commerce le temps du
     * calcul, puis on rend le contexte tel qu'il etait.
     */
    private <T> T pourLEntreprise(Entreprise entreprise, java.util.function.Supplier<T> calcul) {
        SecurityContext avant = SecurityContextHolder.getContext();
        SecurityContext pendant = SecurityContextHolder.createEmptyContext();
        UserDetailsImpl robot = new UserDetailsImpl(null, "resumes", null, null, entreprise.getId(),
                List.of(new SimpleGrantedAuthority(ERole.ROLE_ADMIN.name())));
        pendant.setAuthentication(new UsernamePasswordAuthenticationToken(robot, null, robot.getAuthorities()));
        SecurityContextHolder.setContext(pendant);
        try {
            return calcul.get();
        } finally {
            SecurityContextHolder.setContext(avant);
        }
    }
}
