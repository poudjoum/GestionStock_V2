package com.jumpy.tech.gestionstock.gestiondestock.controller.api;

import com.jumpy.tech.gestionstock.gestiondestock.dto.UserDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static com.jumpy.tech.gestionstock.gestiondestock.utils.Constants.APP_ROOT;

public interface UserControllerApi {
    @Tag(name="Post",description = "Post Methods of Gestion de Stock APIs")
    @PostMapping(value = APP_ROOT+"/users/create",consumes = MediaType.APPLICATION_JSON_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> save(UserDto dto);
    @Tag(name="Get",description = "Get Methods of Gestion de Stock APIs")
    @GetMapping(value = APP_ROOT+"/users/{idUser}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> findById(@PathVariable Long idUser);
    @GetMapping(value = APP_ROOT+"/users/{email}",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserDto> findUserByEmail(@PathVariable String email);
    @GetMapping(value = APP_ROOT+"/users/",produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<UserDto>> findAll();
    @DeleteMapping(value = APP_ROOT+"/users/delete/{idUser}")
   ResponseEntity<UserDto>delete(@PathVariable Long idUser);
}
