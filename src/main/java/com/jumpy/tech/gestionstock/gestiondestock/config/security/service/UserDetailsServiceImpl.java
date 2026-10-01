package com.jumpy.tech.gestionstock.gestiondestock.config.security.service;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CompteClientFidelite;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CompteClientFideliteRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;
    private final CompteClientFideliteRepository compteClientFideliteRepository;

    public UserDetailsServiceImpl(UtilisateurRepository utilisateurRepository,
                                  CompteClientFideliteRepository compteClientFideliteRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.compteClientFideliteRepository = compteClientFideliteRepository;
    }

    /**
     * Un compte du personnel, et lui seul.
     *
     * Les clients de l'application mobile n'y sont pas cherches : la connexion du back-office et le
     * renouvellement de jeton passent par ici, et un client n'a rien a y faire. Son jeton se relit
     * par {@link #chargerClient}.
     */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Utilisateur user = utilisateurRepository.findUtilisateurByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("user not Found with username : " + username));
        return UserDetailsImpl.build(user);
    }

    /**
     * Le client de l'application mobile designe par un jeton client.
     *
     * Un compte desactive cesse d'etre reconnu des la requete suivante, sans attendre que son
     * jeton expire.
     */
    @Transactional
    public UserDetails chargerClient(Long idClient) throws UsernameNotFoundException {
        CompteClientFidelite client = compteClientFideliteRepository.findById(idClient)
                .filter(CompteClientFidelite::isActif)
                .orElseThrow(() -> new UsernameNotFoundException("Client fidelite introuvable ou desactive : " + idClient));
        return UserDetailsImpl.buildClient(client);
    }
}
