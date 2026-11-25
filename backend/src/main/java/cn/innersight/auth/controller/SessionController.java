package cn.innersight.auth.controller;

import static cn.innersight.common.web.SessionCookie.NAME;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import cn.innersight.auth.dto.*;
import cn.innersight.auth.service.IdentityService;
import cn.innersight.common.error.ApiException;
import cn.innersight.common.web.SessionCookie;
import java.time.Duration;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api")
class SessionController {
    private final IdentityService identity;
    private final SessionCookie cookie;
    SessionController(IdentityService identity, SessionCookie cookie) { this.identity = identity; this.cookie = cookie; }

    @PostMapping("/session") Map<String, Boolean> login(@Valid @RequestBody LoginReq req, HttpServletRequest http, HttpServletResponse response) {
        var result = identity.login(http.getRemoteAddr(), req.login(), req.password());
        if (result.status() == 429) throw new ApiException(429, "RATE_LIMIT", "尝试次数过多，请稍后重试。");
        if (result.status() == 401) throw new ApiException(401, "LOGIN_FAILED", "登录名或密码不正确。");
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build(result.token(), Duration.ofHours(8)));
        return Map.of("temporary", result.temporary());
    }
    @GetMapping("/session") ProfileVO session(@CookieValue(value=NAME,required=false) String token) { return identity.current(token).profile(); }
    @DeleteMapping("/session") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@CookieValue(value=NAME,required=false) String token, HttpServletResponse response) {
        identity.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build("", Duration.ZERO));
    }
    @PutMapping("/session/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    void change(@CookieValue(value=NAME,required=false) String token, @Valid @RequestBody ChangeReq req, HttpServletResponse response) {
        identity.changePassword(token, req.current(), req.next());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build("", Duration.ZERO));
    }
}
