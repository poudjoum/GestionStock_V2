package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.Role;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RoleDto {
    private Long id;
    private String roleName;

    private UserDto utilisateur;


    public static RoleDto fromEntity(Role r) {
        if(r==null) {
            return null;
        }
        return RoleDto.builder()
                .id(r.getId())
                .roleName(r.getRoleName())
                .utilisateur(UserDto.fromEntity(r.getUser()))
                .build();
    }
    public static Role toEntity(RoleDto dto) {
        if(dto==null) {
            return null;
        }
        Role r=new Role();
        r.setId(dto.getId());
        r.setRoleName(dto.getRoleName());
        r.setUser(UserDto.toEntity(dto.getUtilisateur()));
        return r;
    }
}
