package com.jumpy.tech.gestionstock.gestiondestock.fidelite;

import com.jumpy.tech.gestionstock.gestiondestock.dto.*;

import java.util.List;

public interface FideliteClientService {

    AuthClientResponseDto inscrire(InscriptionClientDto dto);

    AuthClientResponseDto connecter(ConnexionClientDto dto);

    ProfilClientFideliteDto monProfil(Long idClient);

    List<MagasinPromotionDto> listerMagasinsEnPromotion(Long idClientOptionnel);

    ReclamationResultatDto reclamerTicket(Long idClient, String codeTicket);

    BonDAchatDto convertirPointsEnBon(Long idClient, ConversionPointsDto dto);

    List<BonDAchatDto> listerMesBons(Long idClient);

    List<TicketHistoriqueDto> listerMesTickets(Long idClient);

    BonDAchatDto verifierBon(String codeBon, Long idEntrepriseCaissier);

}
