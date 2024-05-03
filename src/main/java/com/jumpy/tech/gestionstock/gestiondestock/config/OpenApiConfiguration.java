package com.jumpy.tech.gestionstock.gestiondestock.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {
@Bean
    public OpenAPI defineOpenApi(){
    Server server=new Server();
    server.setUrl("http://localhost:9092");
    server.setDescription("Developpement");

    Contact myContact=new Contact();
    myContact.setName("POUDJOUM LEUTCHO Gédéon Rodrigue");
    myContact.setEmail("poudjoumr@gmail.com");
    Info information=new Info()
            .title("Gestion de Stock Management APP")
            .version("1.0")
            .description("Cet api expose les endpoints de la gestion de Stock")
            .contact(myContact);
    return new OpenAPI().info(information).servers(List.of(server));


    }
}
