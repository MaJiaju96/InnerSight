package cn.innersight.integration;

import cn.innersight.auth.dto.ProfileVO;
import cn.innersight.auth.service.AuthenticatedActor;
import cn.innersight.auth.service.IdentityService;
import cn.innersight.common.dto.PageVO;
import cn.innersight.common.web.SessionCookie;
import cn.innersight.health.service.HealthService;
import cn.innersight.role.service.RoleService;
import cn.innersight.user.service.UserService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
}
