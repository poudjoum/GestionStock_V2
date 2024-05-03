package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.entities.CommandeFour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommandeFourRepository extends JpaRepository<CommandeFour,Long> {

    Optional<CommandeFour> findCommandeFourByCode(String code);
}
