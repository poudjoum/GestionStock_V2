package com.jumpy.tech.gestionstock.gestiondestock.config.security;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.AuthTokenFilter;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.EntryPointJwt;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.JwtUtils;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
    private static final String COMPTABLE = "COMPTABLE";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserDetailsServiceImpl userDetailsService;
    private final EntryPointJwt unauthorizedHandler;
    private final JwtUtils jwtUtils;

    /**
     * Les origines autorisees a appeler l'API depuis un navigateur.
     *
     * Sans elles, un front servi sur un autre port se connectait — la route de connexion portait
     * un `@CrossOrigin(origins="*")` isole — puis se faisait refuser toutes les autres requetes
     * par le navigateur, sans que rien cote serveur ne le signale.
     */
    private final List<String> corsOrigines;

    public SecurityConfiguration(UserDetailsServiceImpl userDetailsService, EntryPointJwt unauthorizedHandler,
                                 JwtUtils jwtUtils,
                                 @Value("${app.corsOrigines}") List<String> corsOrigines) {
        this.userDetailsService = userDetailsService;
        this.unauthorizedHandler = unauthorizedHandler;
        this.jwtUtils = jwtUtils;
        this.corsOrigines = corsOrigines;
    }

    /**
     * La politique CORS, posee une fois pour toute l'API.
     *
     * Les identifiants ne sont pas autorises : l'API ne s'appuie sur aucun cookie, le jeton
     * voyage dans l'en-tete `Authorization`. L'autoriser pour rien obligerait a nommer chaque
     * origine sans jamais pouvoir se replier sur l'etoile, et ouvrirait la porte au vol de session
     * par une page tierce le jour ou un cookie apparaitrait.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsOrigines);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Le front lit la pagination dans le corps, pas dans les en-tetes : rien a exposer.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
                // Monte le filtre CORS devant l'autorisation : une requete preliminaire OPTIONS
                // ne porte pas de jeton, et sans cela elle serait refusee avant d'etre traitee.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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

                        // L'inscription d'une entreprise s'arbitre dans le service, comme celle
                        // d'un compte : le super-administrateur, ou une installation qui ne
                        // compte encore aucune entreprise.
                        .requestMatchers(HttpMethod.POST, API + "/entreprises/inscription").permitAll()

                        // Chacun lit son propre compte et change son propre mot de passe : les
                        // seuls points de /users ouverts a tout compte. Ces regles viennent avant
                        // celle des comptes, qui sinon les reserverait a l'administration.
                        //
                        // On ne demande pas a quelqu'un s'il a le droit de savoir qui il est : le
                        // front en a besoin a chaque rechargement de page pour rebatir son menu.
                        .requestMatchers(HttpMethod.GET, API + "/users/moi").authenticated()
                        .requestMatchers(HttpMethod.PATCH, API + "/users/moi/motdepasse").authenticated()

                        // Et chacun lit l'entreprise pour laquelle il travaille. C'est le meme
                        // principe : la maison dont on porte le tablier n'est pas un secret.
                        //
                        // Sans cette ligne, /entreprises/** plus bas la reservait a
                        // l'administration — et le caissier, qui imprime des tickets a l'en-tete
                        // du magasin toute la journee, etait le seul a ne pas pouvoir la lire.
                        .requestMatchers(HttpMethod.GET, API + "/entreprises/mienne").authenticated()

                        // La plateforme : tout ce qui regarde au-dessus des entreprises.
                        //
                        // L'editeur seul, et le service le redit de son cote. Deux fois la meme
                        // regle, parce que ce sont les seules routes du projet qui traversent
                        // volontairement le cloisonnement : celle qui se perd le jour d'un
                        // remaniement ne doit pas emporter l'autre.
                        .requestMatchers(API + "/plateforme/**").hasRole(SUPER_ADMIN)

                        // Corriger l'identite de son propre commerce : son administrateur.
                        //
                        // Cette ligne precede celle des entreprises, qui ferme desormais tout le
                        // reste a l'editeur seul.
                        .requestMatchers(HttpMethod.PUT, API + "/entreprises/mienne")
                            .hasRole(ADMIN)

                        // Rattacher un compte a une entreprise, c'est donner a quelqu'un les
                        // donnees d'un tiers : l'editeur seul.
                        .requestMatchers(HttpMethod.PATCH, API + "/users/*/entreprise/**")
                            .hasRole(SUPER_ADMIN)

                        // Les comptes : l'administration de son propre commerce. Le service
                        // cloisonne, un administrateur ne voit donc que les siens.
                        .requestMatchers(API + "/users/**").hasAnyRole(ADMIN, SUPER_ADMIN)

                        // Les entreprises, en revanche, reviennent a l'editeur seul.
                        //
                        // Elles etaient ouvertes a tout ROLE_ADMIN, et `POST /entreprise/create`
                        // accepte un identifiant dans son corps : l'administrateur d'un commerce
                        // pouvait donc reecrire l'entreprise du voisin en changeant un nombre. Il
                        // lui reste `GET` et `PUT /entreprises/mienne`, nommes plus haut, qui ne
                        // designent jamais que la sienne.
                        .requestMatchers(API + "/entreprise/**", API + "/entreprises/**")
                            .hasRole(SUPER_ADMIN)

                        // Le caissier vend et enregistre les clients qui se presentent ; il ne
                        // cree ni article ni categorie.
                        .requestMatchers(HttpMethod.POST, API + "/ventes/**", API + "/clients/**")
                            .hasAnyRole(ADMIN, MANAGER, CAISSIER)

                        // L'etat du stock porte la valeur du magasin et les marges qu'on en
                        // deduit : c'est une information de gestion, pas de comptoir. Le stock
                        // d'un article reste lisible par tous sur /mouvements.
                        .requestMatchers(HttpMethod.GET, API + "/stock/**")
                            .hasAnyRole(ADMIN, MANAGER, MAGASINIER, COMPTABLE)

                        // La recette du jour n'est pas une information pour tout le monde : la
                        // regle generale ouvre les lectures a tout compte connecte, ce qui
                        // montrerait le chiffre d'affaires au magasinier.
                        .requestMatchers(HttpMethod.GET, API + "/caisse/**")
                            .hasAnyRole(ADMIN, MANAGER, CAISSIER, COMPTABLE)

                        // Encaisser se fait au comptoir : le caissier doit pouvoir enregistrer un
                        // reglement, la ou la regle des factures juste apres l'en exclurait.
                        .requestMatchers(HttpMethod.POST, API + "/factures/*/reglements")
                            .hasAnyRole(ADMIN, MANAGER, CAISSIER, COMPTABLE)
                        // Reprendre un encaissement, en revanche, touche a une recette deja
                        // constatee : cela ne s'improvise pas au comptoir.
                        .requestMatchers(HttpMethod.DELETE, API + "/factures/*/reglements/**")
                            .hasAnyRole(ADMIN, COMPTABLE)

                        // Annuler une facture est un geste comptable, pas un geste de magasin :
                        // sans cette ligne, la regle POST generique l'aurait ouvert au magasinier
                        // et ferme au comptable.
                        .requestMatchers(HttpMethod.POST, API + "/factures/**")
                            .hasAnyRole(ADMIN, MANAGER, COMPTABLE)

                        // Corriger ou annuler une vente revient aux memes roles que la vendre :
                        // c'est au comptoir que l'erreur se constate, et les regles generiques
                        // plus bas en auraient exclu le caissier, qui pouvait pourtant vendre.
                        .requestMatchers(HttpMethod.PATCH, API + "/ventes/**")
                            .hasAnyRole(ADMIN, MANAGER, CAISSIER)
                        .requestMatchers(HttpMethod.DELETE, API + "/ventes/*/lignes/**")
                            .hasAnyRole(ADMIN, MANAGER, CAISSIER)

                        // Ses propres notifications : chacun lit et marque les siennes. Sans
                        // cette ligne, la regle PATCH generique reserverait la lecture d'une
                        // cloche a trois roles sur six.
                        .requestMatchers(HttpMethod.PATCH, API + "/notifications/**").authenticated()

                        // Renoncer a un reliquat n'est pas un constat de magasin mais une
                        // decision : on cesse d'attendre un fournisseur, ou de devoir a un
                        // client. Le magasinier enregistre ce qui arrive — la regle POST
                        // generique juste en dessous lui ouvrirait aussi le droit d'y renoncer.
                        .requestMatchers(HttpMethod.POST, API + "/commandes-fournisseurs/*/cloture",
                                API + "/commandes-clients/*/cloture")
                            .hasAnyRole(ADMIN, MANAGER)

                        // L'inventaire.
                        //
                        // Ouvrir fige le stock de tout le catalogue ; valider le corrige, et
                        // abandonner efface un travail de comptage. Ces trois gestes engagent le
                        // magasin entier : la regle POST generique les ouvrirait au magasinier,
                        // qui compte mais ne decide pas.
                        .requestMatchers(HttpMethod.POST, API + "/inventaires",
                                API + "/inventaires/*/validation", API + "/inventaires/*/abandon")
                            .hasAnyRole(ADMIN, MANAGER)

                        // Se reprendre fait partie du comptage : celui qui note une quantite doit
                        // pouvoir defaire la sienne. La regle DELETE generique, elle, ecarte le
                        // magasinier — et c'est lui qui est dans les rayons.
                        .requestMatchers(HttpMethod.DELETE, API + "/inventaires/*/comptages/**")
                            .hasAnyRole(ADMIN, MANAGER, MAGASINIER)

                        // Un inventaire montre les quantites de tout le magasin, comme l'etat du
                        // stock : la regle GET generique l'ouvrirait a tout compte connecte, donc
                        // au caissier.
                        .requestMatchers(HttpMethod.GET, API + "/inventaires/**")
                            .hasAnyRole(ADMIN, MANAGER, MAGASINIER, COMPTABLE)

                        // Importer un catalogue reecrit les prix de tout le magasin en un appel.
                        // La regle POST generique juste en dessous l'ouvrirait au magasinier :
                        // il tient la marchandise, pas la politique de prix.
                        .requestMatchers(HttpMethod.POST, API + "/articles/import")
                            .hasAnyRole(ADMIN, MANAGER)

                        // Faire avancer une commande — la declarer livree, donc faire entrer la
                        // marchandise en stock — est un geste de magasin, pas une consultation.
                        // Sans cette ligne, PATCH tombait dans le authenticated() final et tout
                        // compte connecte pouvait livrer une commande.
                        .requestMatchers(HttpMethod.PATCH, API + "/**")
                            .hasAnyRole(ADMIN, MANAGER, MAGASINIER)

                        // Remplacer une ressource entiere engage plus que la modifier en partie :
                        // les memes deux roles que la suppression, et non les trois du PATCH.
                        //
                        // Sans cette ligne, un PUT ne rencontrait aucune regle de methode et
                        // tombait dans le `authenticated()` final — ouvert a tout compte connecte.
                        // Le premier PUT de l'application etant celui de l'identite de
                        // l'entreprise, que la regle /entreprises/** ferme deja plus haut, le
                        // probleme ne se voyait pas : il attendait le suivant.
                        .requestMatchers(HttpMethod.PUT, API + "/**").hasAnyRole(ADMIN, MANAGER)

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
