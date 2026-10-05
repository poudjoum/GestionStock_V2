package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Vente;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Builder
@Data
public class VenteDto {
    private Long id;
    private String code;
    private Instant datevente;
    /**
     * L'identite que le poste de vente donne a la vente avant de l'envoyer — un UUID qu'il tire
     * lui-meme.
     *
     * Elle rend l'envoi rejouable sans risque : reposter la meme reference rend la vente deja
     * enregistree au lieu d'en creer une seconde. Facultative en vente directe, obligatoire a la
     * synchronisation.
     */
    private String referenceClient;
    /**
     * Le code imprime sous le QR du ticket. Tire par le poste de vente — un ticket imprime hors
     * ligne doit deja porter le sien — ou par le serveur quand la vente arrive sans.
     */
    private String codeTicket;
    private String Commentaires;
    private boolean annulee;
    /** A qui l'on vend, s'il est connu. Une vente de comptoir anonyme n'en a pas. */
    private ClientDto client;
    /** L'entreprise qui vend : c'est elle qui porte le regime de TVA applique a la facture. */
    private Long idEntreprise;
    /** Nul pour une vente au comptoir ; renseigne quand la vente sert une commande client. */
    private Long idCommandeClient;
    /**
     * Le magasin qui vend. A l'envoi d'une vente faite hors ligne, celui ou se trouvait le poste ;
     * au comptoir, le site actif fait foi.
     */
    private Long idSite;
    private String nomSite;
    /** En lecture : le site d'ou la marchandise est partie. */
    private Long idSiteExpedition;
    private String nomSiteExpedition;
    private List<LigneVenteDto> ligneVente;

    /**
     * Ce que le client a paye au comptoir, pour une vente synchronisee. Ignore partout ailleurs, et
     * jamais rendu.
     *
     * Hors ligne, le poste de vente ne peut ni facturer ni encaisser : il a pourtant pris l'argent.
     * S'il envoyait l'encaissement a part, une fois la connexion revenue, le reglement serait date
     * du jour de l'envoi — l'etat de caisse additionne les reglements par leur date, et les especes
     * de lundi compteraient dans le tiroir de mardi. Et un reglement rejoue apres une reponse
     * perdue serait encaisse deux fois. L'encaissement voyage donc avec la vente, et le serveur fait
     * le tout dans une seule transaction, a la date de la vente.
     *
     * Un montant nul facture sans rien encaisser : la vente a credit.
     */
    private ReglementDto encaissement;
    public static VenteDto fromEntity(Vente vente) {
        if(vente==null) {
            // TODO Auto-generated method stub
            return null;
        }

        return VenteDto.builder()
                .id(vente.getId())
                .code(vente.getCode())
                .datevente(vente.getDatevente())
                .referenceClient(vente.getReferenceClient())
                .codeTicket(vente.getCodeTicket())
                .Commentaires(vente.getCommentaires())
                .annulee(vente.isAnnulee())
                .client(ClientDto.fromEntity(vente.getClient()))
                .idEntreprise(vente.getIdEntreprise())
                .idCommandeClient(vente.getCommandeClient() == null ? null : vente.getCommandeClient().getId())
                .idSite(vente.getSite() == null ? null : vente.getSite().getId())
                .nomSite(vente.getSite() == null ? null : vente.getSite().getNom())
                .idSiteExpedition(vente.getSiteExpedition() == null ? null : vente.getSiteExpedition().getId())
                .nomSiteExpedition(vente.getSiteExpedition() == null ? null : vente.getSiteExpedition().getNom())
                .build();
    }
    public static Vente toEntity(VenteDto dto) {

        if(dto==null) {
            return null;
        }

        Vente ven=new Vente();
        ven.setId(dto.getId());
        ven.setCode(dto.getCode());
        ven.setDatevente(dto.getDatevente());
        ven.setReferenceClient(dto.getReferenceClient());
        ven.setCommentaires(dto.getCommentaires());
        ven.setIdEntreprise(dto.getIdEntreprise());

        return ven;
    }
}
