package it.intesigroup.ums.web;

import it.intesigroup.ums.domain.User;
import it.intesigroup.ums.dto.CreateUserRequest;
import it.intesigroup.ums.dto.UpdateUserRequest;
import it.intesigroup.ums.dto.UserResponse;
import it.intesigroup.ums.mapper.UserMapper;
import it.intesigroup.ums.security.SecurityUtils;
import it.intesigroup.ums.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','OPERATOR','MAINTAINER','DEVELOPER','REPORTER')")
    public Page<UserResponse> list(Pageable pageable) {
        // Solo OWNER/MAINTAINER possono vedere i campi sensibili (es. codice fiscale completo)
        boolean canSeeSensitive = SecurityUtils.hasAnyRole("OWNER", "MAINTAINER");
        return userService.listUsers(pageable)
                .map(u -> UserMapper.toResponse(u, !canSeeSensitive));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','OPERATOR','MAINTAINER','DEVELOPER','REPORTER')")
    public UserResponse get(@PathVariable UUID id) {
        User u = userService.getUser(id);
        boolean canSeeSensitive = SecurityUtils.hasAnyRole("OWNER", "MAINTAINER");
        return UserMapper.toResponse(u, !canSeeSensitive);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MAINTAINER')")
    public ResponseEntity<UserResponse> create(@RequestBody @Valid CreateUserRequest req) {
        User u = userService.createUser(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserMapper.toResponse(u));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MAINTAINER')")
    public UserResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateUserRequest req) {
        User u = userService.updateUser(id, req);
        return UserMapper.toResponse(u);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('OWNER','MAINTAINER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable UUID id) {
        userService.disableUser(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MAINTAINER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@PathVariable UUID id) {
        userService.softDeleteUser(id);
    }
}
