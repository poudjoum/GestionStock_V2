package com.jumpy.tech.gestionstock.gestiondestock;

import org.junit.jupiter.api.Test;

/**
 * Le contexte demarre, migrations comprises.
 *
 * Ce test exigeait jusqu'ici un PostgreSQL installe sur la machine, a la bonne adresse et avec le
 * bon mot de passe : il echouait sur tout poste neuf. Il monte desormais sa propre base.
 */
class GestionDeStockApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
