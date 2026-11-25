package cn.innersight.integration;

import cn.innersight.InnerSightApplication;
import cn.innersight.auth.service.IdentityService;
import cn.innersight.common.error.ApiException;
import cn.innersight.role.service.RoleService;
import cn.innersight.user.service.UserService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="INNERSIGHT_TEST_DB_URL", matches=".+")
class MariaDbAcceptanceTest {
    @Test void initializationAuthorizationRevocationAndStaleWrites() throws Exception {
        String url = System.getenv("INNERSIGHT_TEST_DB_URL");
        assertTrue(url.matches("jdbc:mariadb://[^/?;]+/innersight_test(?:\\?[^\\r\\n]*)?"), "集成测试仅接受独立 innersight_test 库");
        String user = System.getenv("INNERSIGHT_TEST_DB_USER");
        String pass = System.getenv("INNERSIGHT_TEST_DB_PASSWORD");
        assertNotNull(user); assertNotNull(pass);
        var datasource = new org.springframework.jdbc.datasource.DriverManagerDataSource(url, user, pass);
        var preflight = new JdbcTemplate(datasource);
        preflight.queryForObject("SELECT VERSION(), DATABASE()", (rs, row) -> {
            String version = rs.getString(1);
            String database = rs.getString(2);
            System.out.printf("SELECT VERSION(), DATABASE(): %s | %s%n", version, database);
            assertTrue(version.matches("(?:5\\.5\\.5-)?10\\.11\\.19-MariaDB(?:[-+].*)?"),
                    "环境不符，未执行：要求 MariaDB Server 10.11.19");
            assertEquals("innersight_test", database, "环境不符，未执行：不是专用测试库");
            return true;
        });
        assertEquals(0, preflight.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class), "环境不符，未执行：请提供空测试库；本测试不清理旧数据");
        try (var context = new SpringApplicationBuilder(InnerSightApplication.class).web(WebApplicationType.NONE)
                .initializers(app -> app.getEnvironment().getPropertySources().addFirst(
                        new org.springframework.core.env.MapPropertySource("acceptanceDatasource", Map.of(
                                "spring.datasource.url", url, "spring.datasource.username", user,
                                "spring.datasource.password", pass))))
                .run()) {
            var identity = context.getBean(IdentityService.class);
            var users = context.getBean(UserService.class);
            var roleService = context.getBean(RoleService.class);
            var db = context.getBean(JdbcTemplate.class);
            String temporary = UUID.randomUUID().toString(), next = UUID.randomUUID().toString();
            var tasks = Executors.newFixedThreadPool(2);
            try {
                var a = tasks.submit(() -> identity.initialize("root.test", "验收管理员", temporary));
                var b = tasks.submit(() -> identity.initialize("root.test", "验收管理员", temporary));
                assertNotEquals(a.get(), b.get());
            } finally { tasks.shutdownNow(); }
            assertFalse(identity.initialize("root.test", "不会覆盖", UUID.randomUUID().toString()));
            String initial = identity.login("admin", "ROOT.TEST", temporary).token();
            assertTrue(identity.current(initial).profile().temporary());
            assertThrows(ApiException.class, () -> users.users(initial, "", null, 0, 20));
            identity.changePassword(initial, temporary, next);
            assertEquals(401, assertThrows(ApiException.class, () -> identity.current(initial)).status);
            String operator = identity.login("admin", "root.test", next).token();
            assertTrue(identity.current(operator).admin());
            roleService.createRole(operator, "READ_ONLY", "用户只读", Set.of("user:read"));
            roleService.createRole(operator, "READ_OTHER", "第二只读角色", Set.of("user:read"));
            var roles = roleService.roles(operator, "READ_", null, 0, 999);
            assertEquals(100, roles.size()); assertEquals(2, roles.total());
            long roleA = roles.items().get(0).id(), roleB = roles.items().get(1).id();
            String readerPassword = UUID.randomUUID().toString(), readerNext = UUID.randomUUID().toString();
            users.createUser(operator, "reader.test", "只读账号", readerPassword);
            long id = users.users(operator, "reader.test", null, 0, 20).items().get(0).id();
            assertTrue(users.user(operator,id).roles().isEmpty());
            users.assign(operator,id,users.user(operator,id).revision(),Set.of(roleA,roleB));
            String readerInitial = identity.login("reader","reader.test",readerPassword).token();
            identity.changePassword(readerInitial,readerPassword,readerNext);
            String reader = identity.login("reader","reader.test",readerNext).token();
            assertEquals(2, users.users(reader, "", null, 0, 20).total());
            assertEquals(403, assertThrows(ApiException.class, () -> users.createUser(reader,"new.test","无权限",temporary)).status);
            long revision = users.user(operator,id).revision();
            users.assign(operator,id,revision,Set.of(roleB));
            assertEquals(2, users.users(reader, "", null, 0, 20).total());
            assertEquals(409, assertThrows(ApiException.class, () -> users.assign(operator,id,revision,Set.of())).status);
            users.assign(operator,id,users.user(operator,id).revision(),Set.of());
            assertEquals(403, assertThrows(ApiException.class, () -> users.users(reader,"",null,0,20)).status);
            assertEquals(id, identity.current(reader).id());
            users.updateUser(operator,id,users.user(operator,id).revision(),"已停用账号",false);
            assertEquals(401, assertThrows(ApiException.class, () -> identity.current(reader)).status);
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM audit_event WHERE operation='INITIALIZE'",Integer.class));
        }
    }
}
