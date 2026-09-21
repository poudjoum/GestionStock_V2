package com.jumpy.tech.gestionstock.gestiondestock.controller;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt.JwtUtils;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.payload.request.LoginRequest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.payload.request.SignupRequest;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.payload.response.JwtResponse;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.payload.response.MessageResponse;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Role;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Utilisateur;
import com.jumpy.tech.gestionstock.gestiondestock.repository.RoleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.UtilisateurRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*",maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthControler {

     AuthenticationManager authenticationManager;
     UtilisateurRepository userRepository;
     RoleRepository roleRepository;
     PasswordEncoder encoder;
     JwtUtils jwtUtils;

     public AuthControler(UtilisateurRepository userRepository, JwtUtils jwtUtils,PasswordEncoder encoder,RoleRepository roleRepository,AuthenticationManager authenticationManager){
         this.userRepository=userRepository;
         this.jwtUtils=jwtUtils;
         this.encoder=encoder;
         this.roleRepository=roleRepository;
         this.authenticationManager=authenticationManager;
     }

     @PostMapping("/signin")
    public ResponseEntity<?> authencticateUser(@Valid @RequestBody LoginRequest loginRequest){
         Authentication authentication = authenticationManager.authenticate(
                 new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

         SecurityContextHolder.getContext().setAuthentication(authentication);
         String jwt = jwtUtils.generateJwtToken(authentication);

         UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
         List<String> roles = userDetails.getAuthorities().stream()
                 .map(GrantedAuthority::getAuthority)
                 .collect(Collectors.toList());

         return ResponseEntity.ok(new JwtResponse(jwt,
                 userDetails.getId(),
                 userDetails.getUsername(),
                 userDetails.getEmail(),
                 roles));
     }
    /**
     * Cree un compte.
     *
     * Cette route etait ouverte a tous et laissait le demandeur choisir son role, `admin`
     * compris : n'importe qui pouvait se declarer administrateur de l'application. Elle est
     * desormais reservee aux administrateurs, avec une seule exception — une base ou aucun compte
     * n'existe encore, puisqu'il faut bien creer le premier et que personne ne peut alors
     * l'autoriser.
     */
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        verifierDroitDInscription();

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Cet identifiant est déjà pris"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Cette adresse de courriel est déjà utilisée"));
        }

        // Creation du compte
        Utilisateur user = new Utilisateur(signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        Set<String> strRoles = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByRoleName(ERole.ROLE_USER)
                    .orElseThrow(() -> new IllegalStateException(
                            "Rôle absent de la base : la migration V2 qui les crée n'a pas été appliquée"));
            roles.add(userRole);
            user.setRoles(roles);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByRoleName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Rôle absent de la base : la migration V2 qui les crée n'a pas été appliquée"));
                        roles.add(adminRole);

                        break;
                    case "casher":
                        Role modRole = roleRepository.findByRoleName(ERole.ROLE_CAISSIER)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Rôle absent de la base : la migration V2 qui les crée n'a pas été appliquée"));
                        roles.add(modRole);

                        break;
                    case "accounter":
                        Role accounterRole = roleRepository.findByRoleName(ERole.ROLE_COMPTABLE)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Rôle absent de la base : la migration V2 qui les crée n'a pas été appliquée"));
                        roles.add(accounterRole);

                        break;
                    case "manager":
                        Role managerRole = roleRepository.findByRoleName(ERole.ROLE_MANAGER)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Rôle absent de la base : la migration V2 qui les crée n'a pas été appliquée"));
                        roles.add(managerRole);

                        break;
                    case "magasinier":
                        Role magRole = roleRepository.findByRoleName(ERole.ROLE_MAGASINIER)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Rôle absent de la base : la migration V2 qui les crée n'a pas été appliquée"));
                        roles.add(magRole);

                        break;
                    default:
                        Role userRole = roleRepository.findByRoleName(ERole.ROLE_USER)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Rôle absent de la base : la migration V2 qui les crée n'a pas été appliquée"));
                        roles.add(userRole);
                }
            });
            user.setRoles(roles);
        }
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Le compte a été créé"));
    }

    private void verifierDroitDInscription() {
        // Amorcage : sur une base sans aucun compte, la premiere inscription est libre. Elle est
        // la seule, et elle n'a rien a prendre a personne.
        if (userRepository.count() == 0) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean administrateur = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(autorite -> ERole.ROLE_ADMIN.name().equals(autorite.getAuthority()));
        if (!administrateur) {
            throw new AccessDeniedException("Seul un administrateur peut créer un compte");
        }
    }
}