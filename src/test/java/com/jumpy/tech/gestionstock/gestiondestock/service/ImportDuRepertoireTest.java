package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.AbstractIntegrationTest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.dto.EntrepriseDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.RapportImportDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Client;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Fournisseur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ClientRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.FournisseurRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.ImportRepertoireService.Repertoire;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'import des clients et des fournisseurs : on montre avant d'ecrire, on reconnait une fiche a son
 * telephone, et une entreprise se dit par un prenom vide.
 */
class ImportDuRepertoireTest extends AbstractIntegrationTest {

    @Autowired
    private ImportRepertoireService service;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private FournisseurRepository fournisseurRepository;

    private Long idEntreprise;

    @BeforeEach
    void uneEntreprise() {
        idEntreprise = entrepriseService.save(EntrepriseDto.builder()
                .nom("Quincaillerie " + UUID.randomUUID())
                .registreCommerce("RC-" + UUID.randomUUID())
                .email("contact" + UUID.randomUUID() + "@exemple.test")
                .tel("690000000")
                .build()).getId();
        UserDetailsImpl principal = new UserDetailsImpl(1L, "gerant", "gerant@exemple.test",
                "x", idEntreprise, List.of(new SimpleGrantedAuthority(ERole.ROLE_MANAGER.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void oublierLUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private MultipartFile classeur(List<List<Object>> lignes) throws IOException {
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet("Clients");
            List<String> entete = List.of("nom", "prenom", "telephone", "courriel", "adresse", "ville", "pays");
            Row premiere = feuille.createRow(0);
            for (int c = 0; c < entete.size(); c++) {
                premiere.createCell(c).setCellValue(entete.get(c));
            }
            for (int i = 0; i < lignes.size(); i++) {
                Row ligne = feuille.createRow(i + 1);
                for (int c = 0; c < lignes.get(i).size(); c++) {
                    Object v = lignes.get(i).get(c);
                    if (v instanceof Number n) {
                        ligne.createCell(c).setCellValue(n.doubleValue());
                    } else if (v != null) {
                        ligne.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            classeur.write(sortie);
            return new MockMultipartFile("fichier", "repertoire.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", sortie.toByteArray());
        }
    }

    @Test
    void la_simulation_n_ecrit_rien_et_l_ecriture_ecrit_ce_qui_etait_annonce() throws IOException {
        MultipartFile fichier = classeur(List.of(
                List.of("Ngo Bassa", "Aline", "677 11 22 33", "aline@exemple.cm", "Rue Joss", "Douala", "Cameroun"),
                List.of("BTP Wouri SARL", "", "233401020", "achats@btpwouri.cm")));

        RapportImportDto simulation = service.importer(Repertoire.CLIENTS, fichier, true);
        assertThat(simulation.creees()).isEqualTo(2);
        assertThat(clientRepository.findAllByIdEntreprise(idEntreprise)).isEmpty();

        RapportImportDto ecriture = service.importer(Repertoire.CLIENTS, fichier, false);
        assertThat(ecriture.creees()).isEqualTo(2);
        List<Client> clients = clientRepository.findAllByIdEntreprise(idEntreprise);
        assertThat(clients).extracting(Client::getNom).containsExactlyInAnyOrder("Ngo Bassa", "BTP Wouri SARL");
        // L'entreprise n'a pas de prenom : c'est ce qui la fait reconnaitre comme telle a l'ecran.
        assertThat(clients).filteredOn(c -> c.getNom().equals("BTP Wouri SARL"))
                .extracting(Client::getPrenoms).containsOnlyNulls();
    }

    @Test
    void un_fichier_rejoue_met_a_jour_par_le_telephone_au_lieu_de_doubler() throws IOException {
        service.importer(Repertoire.FOURNISSEURS, classeur(List.of(
                List.of("Cimencam", "", "233 50 50 50", "commandes@cimencam.cm"))), false);

        // Le meme numero, ecrit autrement et avec l'indicatif : la meme fiche.
        RapportImportDto rapport = service.importer(Repertoire.FOURNISSEURS, classeur(List.of(
                List.of("Cimencam SA", "", "+237 233505050", "ventes@cimencam.cm"))), false);

        assertThat(rapport.modifiees()).isEqualTo(1);
        assertThat(rapport.creees()).isZero();
        List<Fournisseur> fournisseurs = fournisseurRepository.findAllByIdEntreprise(idEntreprise);
        assertThat(fournisseurs).hasSize(1);
        assertThat(fournisseurs.get(0).getNom()).isEqualTo("Cimencam SA");
    }

    @Test
    void chaque_ligne_refusee_dit_pourquoi_avec_son_numero_de_tableur() throws IOException {
        RapportImportDto rapport = service.importer(Repertoire.CLIENTS, classeur(List.of(
                List.of("Sans telephone", "", "", "a@exemple.cm"),
                List.of("Kamga", "Paul", 699887766, "pas-un-courriel"),
                List.of("Fotso", "Jean", "690000001", "jean@exemple.cm"),
                List.of("Fotso bis", "Jean", "690 000 001", "jean2@exemple.cm"))), true);

        assertThat(rapport.lues()).isEqualTo(4);
        assertThat(rapport.creees()).isEqualTo(1);
        assertThat(rapport.refusees()).extracting(RapportImportDto.LigneRefuseeDto::ligne).containsExactly(2, 3, 5);
        assertThat(rapport.refusees().get(0).raison()).contains("Téléphone absent");
        assertThat(rapport.refusees().get(1).raison()).contains("Courriel illisible");
        assertThat(rapport.refusees().get(2).raison()).contains("déjà ligne 4");
    }

    @Test
    void le_modele_porte_les_colonnes_attendues() throws IOException {
        byte[] modele = service.modele(Repertoire.CLIENTS);
        try (Workbook classeur = WorkbookFactory.create(new ByteArrayInputStream(modele))) {
            Row entete = classeur.getSheetAt(0).getRow(0);
            assertThat(entete.getCell(0).getStringCellValue()).isEqualTo("nom");
            assertThat(entete.getCell(2).getStringCellValue()).isEqualTo("telephone");
        }
        // Et il s'importe tel quel : ses exemples sont valides.
        RapportImportDto rapport = service.importer(Repertoire.CLIENTS, new MockMultipartFile(
                "fichier", "modele.xlsx", "application/octet-stream", modele), true);
        assertThat(rapport.refusees()).isEmpty();
        assertThat(rapport.creees()).isEqualTo(2);
    }
}
