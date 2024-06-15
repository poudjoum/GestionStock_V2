package com.jumpy.tech.gestionstock.gestiondestock.dto;

import com.jumpy.tech.gestionstock.gestiondestock.entities.ERole;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Role;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RoleDto {
    private Long id;
    private ERole roleName;

    public static RoleDto fromEntity(Role r) {
        if(r==null) {
            return null;
        }
        return RoleDto.builder()
                .id(r.getId())
                .roleName(r.getRoleName())

                .build();
    }
    public static Role toEntity(RoleDto dto) {
        if(dto==null) {
            return null;
        }
        Role r=new Role();
        r.setId(dto.getId());
        r.setRoleName(dto.getRoleName());
        return r;
    }
}
