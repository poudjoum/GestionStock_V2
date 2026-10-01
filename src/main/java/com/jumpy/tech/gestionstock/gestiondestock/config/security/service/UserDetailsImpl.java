package com.jumpy.tech.gestionstock.gestiondestock.config.security.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CompteClientFidelite;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class UserDetailsImpl implements UserDetails {

    private Long id ;
    private String username;
    private String email;
    @JsonIgnore
    private String password;
    /**
     * L'entreprise a laquelle ce compte appartient.
     *
     * C'est elle qui delimite tout ce que l'utilisateur voit et ecrit. Elle est portee par le
     * compte, jamais par la requete : un identifiant d'entreprise envoye par l'appelant serait
     * une invitation a travailler chez le voisin.
     */
    private Long idEntreprise;
    /** Un compte ferme ne se connecte plus : Spring refuse l'authentification sur `isEnabled`. */
    private boolean actif = true;
    private Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id,String username,String email,String password,Long idEntreprise,Collection<?extends GrantedAuthority> authorities){
        this(id, username, email, password, idEntreprise, true, authorities);
    }

    public UserDetailsImpl(Long id,String username,String email,String password,Long idEntreprise,boolean actif,Collection<?extends GrantedAuthority> authorities){
        this.id=id;
        this.username=username;
        this.email=email;
        this.password=password;
        this.idEntreprise=idEntreprise;
        this.actif=actif;
        this.authorities=authorities;
    }

    public  static UserDetailsImpl build(Utilisateur utilisateur){
        List<GrantedAuthority> authorities=utilisateur.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.getRoleName().name())).collect(Collectors.toUnmodifiableList());
        return new UserDetailsImpl(
                utilisateur.getId(),
                utilisateur.getUsername(),
                utilisateur.getEmail(),
                utilisateur.getMotdepasse(),
                utilisateur.getEntreprise() == null ? null : utilisateur.getEntreprise().getId(),
                // Le compte peut etre ouvert et le commerce ferme : suspendu a la main, ou son
                // abonnement echu. La porte se ferme alors pour tout le monde a la fois.
                //
                // La regle est ici plutot que dans le service d'authentification parce que deux
                // chemins y menent — la connexion et le renouvellement de jeton — et qu'un
                // abonnement echu qui continuerait de renouveler des jetons ne suspendrait rien.
                //
                // `LocalDate.now()` lit le fuseau de la machine, UTC dans le conteneur, la ou le
                // magasin vit une heure plus tard. Sur une echeance annuelle, la fermeture tombe
                // donc au plus tard une heure apres minuit : cela ne merite pas de faire traverser
                // un fuseau a une methode statique.
                utilisateur.isActif()
                        && (utilisateur.getEntreprise() == null
                            || utilisateur.getEntreprise().accesOuvert(java.time.LocalDate.now())),
                authorities
        );
    }

    /** Le role d'un client de l'application mobile. Il n'ouvre que l'espace fidelite. */
    public static final String ROLE_CLIENT_FIDELITE = "ROLE_CLIENT_FIDELITE";

    /**
     * Un client de l'application mobile.
     *
     * Son nom d'utilisateur n'est pas son telephone mais `client:<id>`. Un telephone est choisi par
     * qui s'inscrit, et un compte du personnel pourrait porter le meme : tout ce qui retrouve un
     * compte par son nom — `authentication.getName()` dans les services — doit tomber a cote, et
     * non sur l'employe.
     */
    public static UserDetailsImpl buildClient(CompteClientFidelite client) {
        return new UserDetailsImpl(
                client.getId(),
                "client:" + client.getId(),
                null,
                client.getMotDePasse(),
                null,
                client.isActif(),
                List.of(new SimpleGrantedAuthority(ROLE_CLIENT_FIDELITE))
        );
    }

    @JsonIgnore
    public boolean isClientFidelite() {
        return authorities != null && authorities.stream()
                .anyMatch(a -> ROLE_CLIENT_FIDELITE.equals(a.getAuthority()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // Renvoyait `true` en dur : un compte ferme se serait connecte comme avant.
        return actif;
    }
}
