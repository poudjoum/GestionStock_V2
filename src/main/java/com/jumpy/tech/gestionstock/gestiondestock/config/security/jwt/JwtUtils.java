package com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt;

import com.jumpy.tech.gestionstock.gestiondestock.config.security.service.UserDetailsImpl;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Fabrication et verification des jetons.
 *
 * Ecrit pour l'API JJWT 0.13. La 0.11 employait `setSubject`, `parserBuilder` et un `signWith`
 * prenant l'algorithme en argument : tout cela etait deprecie depuis la 0.12, l'algorithme etant
 * desormais deduit de la cle — une cle HMAC de 512 bits ne peut servir qu'a HS512.
 */
@Component
@Slf4j
public class JwtUtils {

    @Value("${app.jwtSecret}")
    private String jwrSecret;

    @Value("${app.jwtExpirationMS}")
    private int jwtExpirationMs;

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        Date maintenant = new Date();
        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(maintenant)
                .expiration(new Date(maintenant.getTime() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwrSecret));
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            // `parseSignedClaims` exige une signature valide, la ou l'ancien `parse` acceptait
            // aussi un jeton non signe.
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Jeton JWT mal forme : {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Jeton JWT expire : {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Jeton JWT non pris en charge : {}", e.getMessage());
        } catch (SignatureException e) {
            log.error("Signature du jeton JWT invalide : {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Jeton JWT vide : {}", e.getMessage());
        }
        return false;
    }
}
