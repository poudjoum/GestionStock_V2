package com.jumpy.tech.gestionstock.gestiondestock.validator;

import com.jumpy.tech.gestionstock.gestiondestock.dto.ClientDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.FournisseurDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Un client ou un fournisseur peut etre une entreprise : une raison sociale, pas de prenom. */
class RepertoireValidatorTest {

    @Test
    void une_entreprise_cliente_n_a_pas_besoin_de_prenom() {
        ClientDto entreprise = ClientDto.builder()
                .nom("BTP Wouri SARL").mail("achats@btpwouri.cm").numTel("233401020").build();

        assertThat(ClientValidator.validate(entreprise)).isEmpty();
    }

    @Test
    void un_fournisseur_n_a_pas_besoin_de_prenom() {
        FournisseurDto cimencam = FournisseurDto.builder()
                .nom("Cimencam").mail("commandes@cimencam.cm").tel("233505050").build();

        assertThat(FournisseurValidator.validate(cimencam)).isEmpty();
    }

    @Test
    void le_nom_reste_exige_et_le_message_nomme_le_fournisseur() {
        FournisseurDto sansNom = FournisseurDto.builder().mail("x@exemple.cm").tel("233505050").build();

        assertThat(FournisseurValidator.validate(sansNom)).containsExactly("Veuillez renseigner le nom du fournisseur");
    }
}
