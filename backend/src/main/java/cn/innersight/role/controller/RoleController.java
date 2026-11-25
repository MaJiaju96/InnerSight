package cn.innersight.role.controller;

import static cn.innersight.common.web.SessionCookie.NAME;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import cn.innersight.role.dto.*;
import cn.innersight.role.service.RoleService;
import cn.innersight.common.dto.PageVO;
import java.util.Set;

@RestController
@RequestMapping("/api")
class RoleController {
    private final RoleService service;
    RoleController(RoleService service) { this.service = service; }

    @GetMapping("/roles") PageVO<RoleVO> roles(@CookieValue(value=NAME,required=false) String token,
            @RequestParam(defaultValue="") String search, @RequestParam(required=false) Boolean enabled,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.roles(token, search, enabled, page, size);
    }
    @GetMapping("/roles/{id}") RoleVO role(@CookieValue(value=NAME,required=false) String token, @PathVariable long id) {
        return service.role(token, id);
    }
    @GetMapping("/roles/options") java.util.List<RoleRefVO> roleOptions(@CookieValue(value=NAME,required=false) String token) {
        return service.roleOptions(token);
    }
    @GetMapping("/roles/catalog") Set<String> catalog(@CookieValue(value=NAME,required=false) String token) { return service.catalog(token); }
    @PostMapping("/roles") @ResponseStatus(HttpStatus.NO_CONTENT)
    void createRole(@CookieValue(value=NAME,required=false) String token, @Valid @RequestBody CreateRoleReq req) {
        service.createRole(token, req.code(), req.name(), req.permissions());
    }
    @PutMapping("/roles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateRole(@CookieValue(value=NAME,required=false) String token, @PathVariable long id, @Valid @RequestBody UpdateRoleReq req) {
        service.updateRole(token, id, req.revision(), req.name(), req.enabled(), req.permissions(), req.count());
    }
}
