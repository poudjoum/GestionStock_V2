package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.*;
import com.jumpy.tech.gestionstock.gestiondestock.fidelite.FideliteClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@RestController
@RequestMapping(value = APP_ROOT + "/fidelite", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Fidélité Client & Bons d'Achat", description = "Gestion du programme de fidélité pour l'application mobile client et comptoir")
public class FideliteClientController {

    private final FideliteClientService fideliteService;

    public FideliteClientController(FideliteClientService fideliteService) {
        this.fideliteService = fideliteService;
    }

    // --- Authentification Client Mobile ---

    @PostMapping(value = "/auth/inscription", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Inscription d'un client mobile", description = "Crée un compte fidélité avec numéro de téléphone et mot de passe")
    public ResponseEntity<AuthClientResponseDto> inscrire(@RequestBody InscriptionClientDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fideliteService.inscrire(dto));
    }

    @PostMapping(value = "/auth/connexion", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Connexion d'un client mobile", description = "Authentifie le client et retourne le jeton JWT")
    public ResponseEntity<AuthClientResponseDto> connecter(@RequestBody ConnexionClientDto dto) {
        return ResponseEntity.ok(fideliteService.connecter(dto));
    }

    // --- Découverte & Magasins en Promotion ---

    @GetMapping("/magasins")
    @Operation(summary = "Lister les magasins abonnés en promotion", description = "Retourne la liste des magasins partenaires avec leur taux de points")
    public ResponseEntity<List<MagasinPromotionDto>> magasins(@AuthenticationPrincipal UserDetailsImpl user) {
        Long idClient = (user != null && user.isClientFidelite()) ? user.getId() : null;
        return ResponseEntity.ok(fideliteService.listerMagasinsEnPromotion(idClient));
    }

    // --- Espace Client Fidélité ---

    @GetMapping("/profil")
    @Operation(summary = "Profil fidélité du client connecté", description = "Solde total et détail des points par magasin")
    public ResponseEntity<ProfilClientFideliteDto> monProfil(@AuthenticationPrincipal UserDetailsImpl client) {
        return ResponseEntity.ok(fideliteService.monProfil(client.getId()));
    }

    @PostMapping(value = "/tickets/reclamer", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Scanner / Réclamer un ticket de caisse", description = "Valide le code du ticket et crédite les points au client")
    public ResponseEntity<ReclamationResultatDto> reclamerTicket(@AuthenticationPrincipal UserDetailsImpl client,
                                                                 @RequestBody ReclamationTicketDto dto) {
        return ResponseEntity.ok(fideliteService.reclamerTicket(client.getId(), dto.getCodeTicket()));
    }

    @GetMapping("/tickets/historique")
    @Operation(summary = "Historique des tickets réclamés", description = "Liste tous les tickets scannés par le client connecté")
    public ResponseEntity<List<TicketHistoriqueDto>> mesTickets(@AuthenticationPrincipal UserDetailsImpl client) {
        return ResponseEntity.ok(fideliteService.listerMesTickets(client.getId()));
    }

    @PostMapping(value = "/bons/convertir", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Convertir des points en bon d'achat", description = "Échange des points d'un magasin contre un bon d'achat FCFA")
    public ResponseEntity<BonDAchatDto> convertirPoints(@AuthenticationPrincipal UserDetailsImpl client,
                                                        @RequestBody ConversionPointsDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fideliteService.convertirPointsEnBon(client.getId(), dto));
    }

    @GetMapping("/bons")
    @Operation(summary = "Mes bons d'achat", description = "Liste tous les bons d'achat du client connecté (actifs et utilisés)")
    public ResponseEntity<List<BonDAchatDto>> mesBons(@AuthenticationPrincipal UserDetailsImpl client) {
        return ResponseEntity.ok(fideliteService.listerMesBons(client.getId()));
    }

    // --- Au comptoir : lire un bon avant de l'encaisser ---
    //
    // Il s'encaisse ensuite comme un reglement de la facture, en mode BON_ACHAT : c'est ce qui
    // diminue le reste a payer.

    @GetMapping("/bons/{codeBon}/verifier")
    @Operation(summary = "Vérifier la validité d'un bon d'achat", description = "Vérifie si un bon d'achat est valide, non expiré et utilisable")
    public ResponseEntity<BonDAchatDto> verifierBon(@PathVariable String codeBon,
                                                    @AuthenticationPrincipal UserDetailsImpl caissier) {
        Long idEntreprise = caissier != null ? caissier.getIdEntreprise() : null;
        return ResponseEntity.ok(fideliteService.verifierBon(codeBon, idEntreprise));
    }
}
