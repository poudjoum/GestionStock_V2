package com.jumpy.tech.gestionstock.gestiondestock.service;

import com.jumpy.tech.gestionstock.gestiondestock.dto.CategoryDto;

import java.util.List;

public interface CategoryService {
    CategoryDto save(CategoryDto dto);
    CategoryDto findById(Long id);
    List<CategoryDto> findAll();
    CategoryDto findByCode(String code);

    void delete(Long id);
}
