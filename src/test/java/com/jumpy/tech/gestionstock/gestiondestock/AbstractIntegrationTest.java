package com.jumpy.tech.gestionstock.gestiondestock;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;

/**
 * Socle des tests d'integration : un PostgreSQL jetable, migre par Flyway comme la production.
 *
 * Le conteneur demarre une fois pour toute la campagne, dans un bloc statique, et n'est jamais
 * arrete — Ryuk, le gardien de Testcontainers, s'en charge quand la JVM se termine. Ce motif du
 * « conteneur unique » n'est pas une optimisation : avec `@Testcontainers` et `@Container`, JUnit
 * arrete le conteneur a la fin de chaque classe de test, tandis que Spring garde ses contextes en
 * cache et les reutilise pour la classe suivante. La deuxieme classe se connectait alors a une
 * base qui n'existait plus et echouait sur « Could not open JPA EntityManager for transaction ».
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            // La minute accordee par defaut ne suffit pas sur une machine qui fait deja tourner
            // d'autres conteneurs : l'initialisation du cluster y depasse regulierement ce delai,
            // et l'echec ressemble alors a une panne du test plutot qu'a une machine chargee.
            .withStartupTimeout(Duration.ofMinutes(3));

    static {
        POSTGRES.start();
    }
}
