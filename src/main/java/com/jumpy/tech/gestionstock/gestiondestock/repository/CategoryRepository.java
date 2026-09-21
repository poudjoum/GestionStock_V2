package com.jumpy.tech.gestionstock.gestiondestock.repository;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category,Long> {
    Optional<Category>findCategoriesByCodeCat(String code);

    java.util.List<Category> findAllByIdEntreprise(Long idEntreprise);

    Optional<Category> findCategoriesByCodeCatAndIdEntreprise(String code, Long idEntreprise);
}
