package cn.innersight.integration;

import cn.innersight.auth.dto.ProfileVO;
import cn.innersight.auth.service.AuthenticatedActor;
import cn.innersight.auth.service.IdentityService;
import cn.innersight.common.dto.PageVO;
import cn.innersight.common.web.SessionCookie;
import cn.innersight.health.service.HealthService;
import cn.innersight.role.service.RoleService;
import cn.innersight.user.dto.UserVO;
import cn.innersight.user.service.UserService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.CannotCreateTransactionException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(properties = {"innersight.origin=http://localhost:5173", "innersight.secure-cookie=true"})
@Import(SessionCookie.class)
class WebContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean IdentityService identity;
    @MockitoBean UserService users;
    @MockitoBean RoleService roles;
    @MockitoBean HealthService health;

    @Test void healthRoutesKeepResponseShape() throws Exception {
        when(health.ready()).thenReturn(true, false);
        mvc.perform(get("/api/health/live")).andExpect(status().isOk()).andExpect(jsonPath("$.service").value("up"));
        mvc.perform(get("/api/health/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.database").value("up"));
        mvc.perform(get("/api/health/ready")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.database").value("down"));
    }

    @Test void sessionReturnsProfileWithoutInternalActorWrapper() throws Exception {
        when(identity.current("test-session")).thenReturn(new AuthenticatedActor(new ProfileVO(1, "reader", "只读", false, false, List.of(), Set.of("user:read"))));
        mvc.perform(get("/api/session").cookie(new Cookie("innersight_session", "test-session")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.login").value("reader"))
                .andExpect(jsonPath("$.permissions[0]").value("user:read")).andExpect(jsonPath("$.profile").doesNotExist());
    }

    @Test void managementRoutesKeepPaginationAndCsrfBoundary() throws Exception {
        when(users.users(null, "", null, 0, 20)).thenReturn(new PageVO<>(List.of(), 0, 0, 20));
        when(roles.catalog(null)).thenReturn(Set.of("role:read"));
        mvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/roles/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("role:read"));
        mvc.perform(post("/api/users").servletPath("/api/users").header("Origin", "https://invalid.example"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ORIGIN_REJECTED"));
    }

    @Test void loginIssuesProtectedCookieAndDoesNotReturnSessionTokenInBody() throws Exception {
        when(identity.login(anyString(), anyString(), anyString()))
                .thenReturn(new IdentityService.LoginResult("contract-test-token", true, 200));
        mvc.perform(post("/api/session").servletPath("/api/session")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"reader","password":"contract-test-only"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"temporary":true}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andExpect(cookie().value(SessionCookie.NAME, "contract-test-token"))
                .andExpect(cookie().httpOnly(SessionCookie.NAME, true))
                .andExpect(cookie().secure(SessionCookie.NAME, true))
                .andExpect(cookie().sameSite(SessionCookie.NAME, "Strict"))
                .andExpect(cookie().path(SessionCookie.NAME, "/api"))
                .andExpect(cookie().maxAge(SessionCookie.NAME, 28800));
    }

    @Test void logoutRevokesSessionAndExpiresCookie() throws Exception {
        mvc.perform(delete("/api/session").servletPath("/api/session")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .cookie(new Cookie(SessionCookie.NAME, "contract-test-token")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value(SessionCookie.NAME, ""))
                .andExpect(cookie().maxAge(SessionCookie.NAME, 0))
                .andExpect(cookie().path(SessionCookie.NAME, "/api"));
        verify(identity).logout("contract-test-token");
    }

    @Test void rejectedLoginsDoNotIssueCookies() throws Exception {
        when(identity.login(anyString(), anyString(), anyString())).thenReturn(
                new IdentityService.LoginResult(null, false, 401),
                new IdentityService.LoginResult(null, false, 429));
        var request = post("/api/session").servletPath("/api/session")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"login":"reader","password":"contract-test-only"}
                        """);
        mvc.perform(request).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(request).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test void databaseReadFailureIsNotAnEmptyListAndRetryReturnsData() throws Exception {
        var user = new UserVO(7, "reader", "只读账号", true, 1, List.of());
        when(users.users("contract-test-token", "", null, 0, 20))
                .thenThrow(new DataAccessResourceFailureException("fixture: secret database endpoint"))
                .thenReturn(new PageVO<>(List.of(user), 1, 0, 20));
        var request = get("/api/users").servletPath("/api/users")
                .cookie(new Cookie(SessionCookie.NAME, "contract-test-token"));
        expectUnavailable(mvc.perform(request));
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].login").value("reader"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test void transactionUnavailableDoesNotReturnSuccessfulWrite() throws Exception {
        doThrow(new CannotCreateTransactionException("fixture: database connection details"))
                .when(users).updateUser("contract-test-token", 7, 1, "新显示名", true);
        expectUnavailable(mvc.perform(put("/api/users/7").servletPath("/api/users/7")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .cookie(new Cookie(SessionCookie.NAME, "contract-test-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"revision":1,"name":"新显示名","enabled":true}
                        """)));
        verify(users).updateUser("contract-test-token", 7, 1, "新显示名", true);
    }

    private static void expectUnavailable(ResultActions result) throws Exception {
        result.andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"code":"UNAVAILABLE","message":"服务暂不可用，请稍后重试。"}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
