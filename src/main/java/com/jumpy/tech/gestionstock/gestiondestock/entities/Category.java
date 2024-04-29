package com.jumpy.tech.gestionstock.gestiondestock.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name="Category")

public class Category extends AbstractEntity {
    @Column(name="code_cat")
    private String codeCat;
    @Column(name="designation")
    private String designation;
    @Column(name="entreprise_id")
    private Long idEntreprise;
    @OneToMany(mappedBy = "category")
    private List<Article> articles;
}
