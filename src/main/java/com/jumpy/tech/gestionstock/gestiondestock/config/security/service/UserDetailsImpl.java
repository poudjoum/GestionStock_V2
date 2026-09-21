package com.jumpy.tech.gestionstock.gestiondestock.config.security.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
                utilisateur.isActif(),
                authorities
        );
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
