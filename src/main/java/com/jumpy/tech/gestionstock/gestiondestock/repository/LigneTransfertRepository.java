package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.LigneTransfert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LigneTransfertRepository extends JpaRepository<LigneTransfert, Long> {

    List<LigneTransfert> findAllByTransfertIdOrderByIdAsc(Long idTransfert);

    List<LigneTransfert> findAllByTransfertIdIn(Collection<Long> idsTransferts);
}
