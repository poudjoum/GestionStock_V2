package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.TicketReclame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketReclameRepository extends JpaRepository<TicketReclame, Long> {

    Optional<TicketReclame> findByCodeTicket(String codeTicket);

    boolean existsByCodeTicket(String codeTicket);

    List<TicketReclame> findAllByClientIdOrderByDateReclamationDesc(Long clientId);
}
