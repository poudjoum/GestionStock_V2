package com.jumpy.tech.gestionstock.gestiondestock.config.security;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.AuthTokenFilter;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.EntryPointJwt;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.JwtUtils;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration {

    /** APP_ROOT ne porte pas de barre oblique initiale, la voici pour les motifs d'URL. */
    private static final String API = "/" + APP_ROOT;

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String MAGASINIER = "MAGASINIER";
    private static final String CAISSIER = "CAISSIER";

    private final UserDetailsServiceImpl userDetailsService;
    private final EntryPointJwt unauthorizedHandler;
    private final JwtUtils jwtUtils;

    public SecurityConfiguration(UserDetailsServiceImpl userDetailsService, EntryPointJwt unauthorizedHandler,
                                 JwtUtils jwtUtils) {
        this.userDetailsService = userDetailsService;
        this.unauthorizedHandler = unauthorizedHandler;
        this.jwtUtils = jwtUtils;
    }

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter(jwtUtils, userDetailsService);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        // Le service d'utilisateurs se fournit au constructeur depuis Spring Security 6.4 : le
        // constructeur vide suivi de setUserDetailsService est deprecie, un fournisseur sans
        // service d'utilisateurs n'ayant jamais eu de sens.
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncode());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncode() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Les regles d'acces.
     *
     * Elles se terminaient jusqu'ici par `anyRequest().permitAll()` : le filtre JWT etait monte,
     * les roles existaient en base, et l'API entiere restait ouverte a qui connaissait une URL.
     *
     * L'ordre compte : Spring retient la premiere regle dont le motif correspond. Les regles
     * d'ecriture, plus etroites, precedent donc la lecture, qui precede le fourre-tout final.
     * Ce dernier est `authenticated()` et non `permitAll()` : une route ajoutee demain naitra
     * fermee, et son auteur aura a decider qui l'ouvre.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                // Aucune session : le jeton porte l'identite a chaque requete.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Se connecter ne demande pas d'etre connecte. L'inscription reste ouverte
                        // ici, mais AuthControler ne l'accorde qu'a un administrateur — sauf sur
                        // une base sans aucun compte, ou il faut bien creer le premier.
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/api-GestionStock/**").permitAll()
                        // Sans quoi une erreur redirigee vers /error se transforme en 403 et
                        // masque la cause reelle.
                        .requestMatchers("/error").permitAll()

                        // Le stock se lit largement et ne s'ecrit qu'au magasin.
                        .requestMatchers(HttpMethod.POST, API + "/mouvements/**")
                            .hasAnyRole(ADMIN, MANAGER, MAGASINIER)

                        // Comptes et entreprises : l'administration seule.
                        .requestMatchers(API + "/users/**", API + "/entreprise/**", API + "/entreprises/**")
                            .hasRole(ADMIN)

                        // Le caissier vend et enregistre les clients qui se presentent ; il ne
                        // cree ni article ni categorie.
                        .requestMatchers(HttpMethod.POST, API + "/ventes/**", API + "/clients/**")
                            .hasAnyRole(ADMIN, MANAGER, CAISSIER)

                        // Faire avancer une commande — la declarer livree, donc faire entrer la
                        // marchandise en stock — est un geste de magasin, pas une consultation.
                        // Sans cette ligne, PATCH tombait dans le authenticated() final et tout
                        // compte connecte pouvait livrer une commande.
                        .requestMatchers(HttpMethod.PATCH, API + "/**")
                            .hasAnyRole(ADMIN, MANAGER, MAGASINIER)

                        // Supprimer engage plus que creer : deux roles, pas cinq.
                        .requestMatchers(HttpMethod.DELETE, API + "/**").hasAnyRole(ADMIN, MANAGER)
                        .requestMatchers(HttpMethod.POST, API + "/**").hasAnyRole(ADMIN, MANAGER, MAGASINIER)

                        // Tout compte valide peut consulter.
                        .requestMatchers(HttpMethod.GET, API + "/**").authenticated()

                        .anyRequest().authenticated()
                );
        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
