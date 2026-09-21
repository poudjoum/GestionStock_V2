package com.jumpy.tech.gestionstock.gestiondestock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableJpaAuditing
// L'expediteur vide la file des courriels a intervalle regulier. C'est la seule tache planifiee :
// tout le reste se declenche sur une requete.
@EnableScheduling
public class GestionDeStockApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestionDeStockApplication.class, args);
    }

}
