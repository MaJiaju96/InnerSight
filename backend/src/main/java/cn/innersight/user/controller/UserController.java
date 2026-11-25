package cn.innersight.user.controller;

import static cn.innersight.common.web.SessionCookie.NAME;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import cn.innersight.user.dto.*;
import cn.innersight.user.service.UserService;
import cn.innersight.common.dto.PageVO;

@RestController
@RequestMapping("/api")
class UserController {
    private final UserService service;
    UserController(UserService service) { this.service = service; }

    @GetMapping("/users") PageVO<UserVO> users(@CookieValue(value=NAME,required=false) String token,
            @RequestParam(defaultValue="") String search, @RequestParam(required=false) Boolean enabled,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.users(token, search, enabled, page, size);
    }
    @GetMapping("/users/{id}") UserVO user(@CookieValue(value=NAME,required=false) String token, @PathVariable long id) {
        return service.user(token, id);
    }
    @PostMapping("/users") @ResponseStatus(HttpStatus.NO_CONTENT)
    void createUser(@CookieValue(value=NAME,required=false) String token, @Valid @RequestBody CreateUserReq req) {
        service.createUser(token, req.login(), req.name(), req.password());
    }
    @PutMapping("/users/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateUser(@CookieValue(value=NAME,required=false) String token, @PathVariable long id, @Valid @RequestBody UpdateUserReq req) {
        service.updateUser(token, id, req.revision(), req.name(), req.enabled());
    }
    @PutMapping("/users/{id}/roles") @ResponseStatus(HttpStatus.NO_CONTENT)
    void assign(@CookieValue(value=NAME,required=false) String token, @PathVariable long id, @Valid @RequestBody AssignmentReq req) {
        service.assign(token, id, req.revision(), req.roles());
    }
    @PutMapping("/users/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@CookieValue(value=NAME,required=false) String token, @PathVariable long id, @Valid @RequestBody ResetReq req) {
        service.reset(token, id, req.revision(), req.password());
    }
}
