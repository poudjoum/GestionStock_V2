package com.jumpy.tech.gestionstock.gestiondestock.config.security.service;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    UtilisateurRepository utilisateurRepository;
    public UserDetailsServiceImpl(UtilisateurRepository utilisateurRepository){
        this.utilisateurRepository=utilisateurRepository;
    }
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Utilisateur user=utilisateurRepository.findUtilisateurByUsername(username)
                .orElseThrow(()-> new UsernameNotFoundException("user not Found with username : "+username));
        return UserDetailsImpl.build(user);
    }
}
