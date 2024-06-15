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
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        Utilisateur user = new Utilisateur(signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        Set<String> strRoles = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByRoleName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
            user.setRoles(roles);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByRoleName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(adminRole);

                        break;
                    case "casher":
                        Role modRole = roleRepository.findByRoleName(ERole.ROLE_CAISSIER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(modRole);

                        break;
                    case "accounter":
                        Role accounterRole = roleRepository.findByRoleName(ERole.ROLE_COMPTABLE)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(accounterRole);

                        break;
                    case "manager":
                        Role managerRole = roleRepository.findByRoleName(ERole.ROLE_MANAGER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(managerRole);

                        break;
                    case "magasinier":
                        Role magRole = roleRepository.findByRoleName(ERole.ROLE_MAGASINIER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(magRole);

                        break;
                    default:
                        Role userRole = roleRepository.findByRoleName(ERole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(userRole);
                }
            });
            user.setRoles(roles);
        }
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}