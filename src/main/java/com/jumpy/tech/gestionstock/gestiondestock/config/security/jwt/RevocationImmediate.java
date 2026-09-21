package com.jumpy.tech.gestionstock.gestiondestock.config.security.jwt;

import com.jumpy.tech.gestionstock.gestiondestock.repository.JetonRafraichissementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Une revocation qui survit au refus qui la suit.
 *
 * Quand on detecte un jeton copie, on ferme le compte puis on refuse la requete. Ecrit dans la
 * meme transaction, le refus annulait la fermeture : l'exception faisait un rollback, et le
 * voleur repartait avec des jetons toujours valides. Le test l'a montre — la deuxieme tentative
 * passait.
 *
 * D'ou une transaction a part. Elle vit dans sa propre classe parce qu'un appel a `this` ne
 * traverse pas le proxy de Spring : la propagation y serait ignoree, et le defaut reviendrait
 * sans rien changer d'apparent.
 *
 * A ne pas confondre avec la revocation ordinaire — fermer un acces, changer un mot de passe —
 * qui doit au contraire tomber avec l'operation qui la porte si celle-ci echoue.
 */
@Service
public class RevocationImmediate {

    private final JetonRafraichissementRepository repository;

    public RevocationImmediate(JetonRafraichissementRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int fermerTout(Long idUtilisateur, Instant maintenant) {
        return repository.revoquerTousPourUtilisateur(idUtilisateur, maintenant);
    }
}
