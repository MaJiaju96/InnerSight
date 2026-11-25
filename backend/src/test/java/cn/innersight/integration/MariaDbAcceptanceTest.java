package cn.innersight.integration;

import cn.innersight.InnerSightApplication;
import cn.innersight.auth.service.IdentityService;
import cn.innersight.common.error.ApiException;
import cn.innersight.role.service.RoleService;
import cn.innersight.user.service.UserService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import cn.innersight.common.dto.PageVO;
import cn.innersight.role.repository.RoleRepository;
import cn.innersight.user.repository.UserRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="INNERSIGHT_TEST_DB_URL", matches=".+")
class MariaDbAcceptanceTest {
    @Test void initializationAuthorizationRevocationAndStaleWrites() throws Exception {
        var settings = checkedDatabase();
        var preflight = settings.jdbc();
        assertEquals(0, preflight.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class), "环境不符，未执行：请提供空测试库；本测试不清理旧数据");
        var clock = new TestClock();
        try (var context = openContext(settings, clock, true)) {
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
            verifySessionBoundaries(identity, clock, "root.test", next, "reader.test", readerNext);
            verifyListPerformance(db, context.getBean(PlatformTransactionManager.class),
                    context.getBean(UserRepository.class), context.getBean(RoleRepository.class));
            verifyConcurrentAdminAndStaleWrites(identity, users, roleService, db, operator, next, id, roleA, roleB);
        }
    }

    // 仅验证已迁移专用库；不修改或复用上一次的验收账号。
    @Test
    @EnabledIfEnvironmentVariable(named="INNERSIGHT_TEST_EXISTING_SCHEMA", matches="true")
    void sessionBoundariesOnExistingTestSchema() {
        var settings = checkedDatabase();
        assertEquals(1, settings.jdbc().queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=TRUE", Integer.class));
        var clock = new TestClock();
        try (var context = openContext(settings, clock, false)) {
            var identity = context.getBean(IdentityService.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.executeWithoutResult(status -> {
                status.setRollbackOnly();
                String suffix = UUID.randomUUID().toString().substring(0, 12);
                String login = "ac02.active." + suffix, disabled = "ac02.disabled." + suffix;
                String password = UUID.randomUUID().toString(), temporary = UUID.randomUUID().toString();
                long account = identity.createAccount(login, "会话边界验收", temporary);
                identity.replacePassword(identity.find(account), password, false);
                long disabledId = identity.createAccount(disabled, "停用验收", password);
                context.getBean(JdbcTemplate.class).update("UPDATE account SET enabled=FALSE WHERE id=?", disabledId);
                verifySessionBoundaries(identity, clock, login, password, disabled, password);
            });
        }
    }

    private record TestDatabase(String url, String user, String password, JdbcTemplate jdbc) {}

    private static TestDatabase checkedDatabase() {
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
        return new TestDatabase(url, user, pass, preflight);
    }

    private static org.springframework.context.ConfigurableApplicationContext openContext(
            TestDatabase settings, TestClock clock, boolean migrate) {
        return new SpringApplicationBuilder(InnerSightApplication.class).web(WebApplicationType.NONE)
                .initializers(app -> {
                    var clockBean = new org.springframework.beans.factory.support.RootBeanDefinition(Clock.class, () -> clock);
                    clockBean.setPrimary(true);
                    ((org.springframework.beans.factory.support.BeanDefinitionRegistry) app.getBeanFactory())
                            .registerBeanDefinition("acceptanceClock", clockBean);
                    app.getEnvironment().getPropertySources().addFirst(
                            new org.springframework.core.env.MapPropertySource("acceptanceDatasource", Map.of(
                                    "spring.datasource.url", settings.url(), "spring.datasource.username", settings.user(),
                                    "spring.datasource.password", settings.password(), "spring.flyway.enabled", migrate)));
                }).run();
    }

    private static void verifySessionBoundaries(IdentityService identity, TestClock clock,
                                                String login, String adminPassword, String disabled, String readerPassword) {
        assertEquals(401, identity.login(login, "missing.account", adminPassword).status());
        assertEquals(401, identity.login(login, disabled, readerPassword).status());
        for (int i = 0; i < 5; i++) {
            assertEquals(401, identity.login(login, " " + login.toUpperCase(Locale.ROOT) + " ", "wrong-value").status());
        }
        assertEquals(429, identity.login(login, login, adminPassword).status());
        clock.advance(599);
        assertEquals(429, identity.login(login, login, adminPassword).status());
        clock.advance(1);
        String idle = identity.login(login, login, adminPassword).token();
        assertNotNull(idle);
        clock.advance(1800);
        assertEquals(401, assertThrows(ApiException.class, () -> identity.current(idle)).status);

        String absolute = identity.login(login, login, adminPassword).token();
        long elapsed = 0;
        while (elapsed + 1700 < 28800) {
            clock.advance(1700);
            elapsed += 1700;
            assertEquals(login, identity.current(absolute).profile().login());
        }
        clock.advance(28800 - elapsed);
        assertEquals(401, assertThrows(ApiException.class, () -> identity.current(absolute)).status);
        String fresh = identity.login(login, login, adminPassword).token();
        assertEquals(login, identity.current(fresh).profile().login());
        identity.logout(fresh);
        assertEquals(401, assertThrows(ApiException.class, () -> identity.current(fresh)).status);
        clock.reset();
    }

    private static final class TestClock extends Clock {
        private final Instant start = Instant.now();
        private volatile Instant current = start;
        void advance(long seconds) { current = current.plusSeconds(seconds); }
        void reset() { current = start; }
        @Override public Instant instant() { return current; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
    }

    private static void verifyListPerformance(JdbcTemplate db, PlatformTransactionManager manager,
                                              UserRepository users, RoleRepository roles) {
        var snapshot = new TransactionTemplate(manager);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.executeWithoutResult(tx -> {
            // 测试样本、绑定与权限一律回滚；同一事务的连接用于采集 MariaDB 会话计数。
            tx.setRollbackOnly();
            int originalUsers = db.queryForObject("SELECT COUNT(*) FROM account", Integer.class);
            int originalRoles = db.queryForObject("SELECT COUNT(*) FROM access_role", Integer.class);
            int sampleUsers = 1000 - originalUsers, sampleRoles = 20 - originalRoles;
            assertTrue(sampleUsers >= 100 && sampleRoles >= 10);
            db.batchUpdate("""
                    INSERT INTO account(login, display_name, secret_hash, enabled, created_at)
                    VALUES (?, ?, 'not-a-password-hash', ?, '2024-01-01 00:00:00')
                    """, IntStream.range(0, sampleUsers).mapToObj(i -> new Object[]{
                    "ac11.user." + i, "AC11 Sample " + i, i % 2 == 0}).toList());
            db.batchUpdate("""
                    INSERT INTO access_role(code, name, enabled, created_at)
                    VALUES (?, ?, ?, '2024-01-01 00:00:00')
                    """, IntStream.range(0, sampleRoles).mapToObj(i -> new Object[]{
                    "AC11_ROLE_" + i, "AC11 Role " + i, i % 2 == 0}).toList());
            db.update("""
                    INSERT INTO account_role(account_id, role_id)
                    SELECT a.id, r.id FROM account a CROSS JOIN access_role r
                    WHERE a.login LIKE 'ac11.user.%' AND r.code = 'AC11_ROLE_0'
                    """);
            db.update("""
                    INSERT INTO role_permission(role_id, permission)
                    SELECT id, 'user:read' FROM access_role WHERE code = 'AC11_ROLE_0'
                    """);

            var small = measure(db, () -> users.users("AC11 Sample", null, 0, 20));
            var large = measure(db, () -> users.users("AC11 Sample", null, 0, 1000));
            assertEquals(20, small.data().items().size());
            assertEquals(100, large.data().items().size());
            assertEquals(100, large.data().size());
            assertEquals(3, small.selects(), "用户列表应只有总数、分页及一条批量角色摘要查询");
            assertEquals(small.selects(), large.selects(), "角色摘要查询次数不能随页大小增长");
            assertEquals(small.data().items(), large.data().items().subList(0, 20));
            assertTrue(large.data().items().stream().allMatch(u -> u.roles().size() == 1
                    && u.roles().get(0).code().equals("AC11_ROLE_0")));
            assertEquals(1000, users.users("", null, 0, 20).total());
            assertEquals(20, roles.roles("", null, 0, 20).total());

            var seen = new ArrayList<String>();
            for (int index = 0; index * 100 < sampleUsers; index++) {
                var page = users.users("AC11 Sample", null, index, 100);
                assertEquals(sampleUsers, page.total());
                page.items().forEach(u -> seen.add(u.login()));
            }
            assertEquals(sampleUsers, new HashSet<>(seen).size());
            assertEquals(IntStream.iterate(sampleUsers - 1, i -> i >= 0, i -> i - 1)
                    .mapToObj(i -> "ac11.user." + i).toList(), seen, "同一创建时间按唯一标识倒序，分页无遗漏");
            var enabled = users.users("ac11.user.", true, 0, 20);
            assertEquals((sampleUsers + 1) / 2, enabled.total());
            assertTrue(enabled.items().stream().allMatch(user -> user.enabled()));
            var filteredRoles = roles.roles("AC11_ROLE_", true, 0, 1000);
            assertEquals(100, filteredRoles.size());
            assertEquals((sampleRoles + 1) / 2, filteredRoles.total());
            assertEquals(IntStream.iterate((sampleRoles - 1) / 2 * 2, i -> i >= 0, i -> i - 2)
                    .mapToObj(i -> "AC11_ROLE_" + i).toList(), filteredRoles.items().stream().map(role -> role.code()).toList());
            var boundRole = filteredRoles.items().stream().filter(role -> role.code().equals("AC11_ROLE_0"))
                    .findFirst().orElseThrow();
            assertEquals(sampleUsers, boundRole.count());
            assertEquals(Set.of("user:read"), boundRole.permissions());
            System.out.printf(Locale.ROOT, "AC-11: users=1000 roles=20; page20=%d SELECT/%.2f ms; page100=%d SELECT/%.2f ms%n",
                    small.selects(), small.elapsedNanos() / 1_000_000.0, large.selects(), large.elapsedNanos() / 1_000_000.0);
        });
    }

    private record PageMeasurement<T>(PageVO<T> data, long selects, long elapsedNanos) {}

    private static <T> PageMeasurement<T> measure(JdbcTemplate db, Supplier<PageVO<T>> query) {
        long initial = selectCount(db);
        long started = System.nanoTime();
        var result = query.get();
        long elapsed = System.nanoTime() - started;
        return new PageMeasurement<>(result, selectCount(db) - initial, elapsed);
    }

    private static long selectCount(JdbcTemplate db) {
        return db.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'", (rs, row) -> rs.getLong(2));
    }

    private void verifyConcurrentAdminAndStaleWrites(IdentityService identity, UserService users, RoleService roles,
                                                     JdbcTemplate db, String operator, String operatorPassword,
                                                     long readerId, long roleA, long roleB) throws Exception {
        var owner = users.user(operator, identity.current(operator).id());
        long adminRole = owner.roles().stream().filter(role -> role.code().equals("ADMIN")).findFirst().orElseThrow().id();
        assertEquals(403, assertThrows(ApiException.class,
                () -> users.updateUser(operator, owner.id(), owner.revision(), owner.name(), false)).status);
        assertEquals(403, assertThrows(ApiException.class,
                () -> users.assign(operator, owner.id(), owner.revision(), Set.of())).status);
        assertEquals(owner, users.user(operator, owner.id()));

        String temp = UUID.randomUUID().toString(), password = UUID.randomUUID().toString();
        users.createUser(operator, "second.admin", "第二位管理员", temp);
        var peer = users.users(operator, "second.admin", null, 0, 20).items().get(0);
        users.assign(operator, peer.id(), peer.revision(), Set.of(adminRole));
        identity.changePassword(identity.login("second", peer.login(), temp).token(), temp, password);
        String second = identity.login("second", peer.login(), password).token();
        var secondAdmin = users.user(operator, peer.id());

        // AC-09：两个管理员提交同一旧版本，成功方的结果保留。
        var reader = users.user(operator, readerId);
        users.assign(operator, readerId, reader.revision(), Set.of(roleA));
        assertConflict(() -> users.assign(second, readerId, reader.revision(), Set.of(roleB)));
        var assigned = users.user(operator, readerId);
        assertEquals(List.of(roleA), assigned.roles().stream().map(role -> role.id()).toList());
        assertThrows(ApiException.class,
                () -> users.assign(second, readerId, assigned.revision(), Set.of(roleB, Long.MAX_VALUE)));
        assertEquals(assigned, users.user(operator, readerId));

        users.updateUser(operator, readerId, assigned.revision(), "第一次修改", assigned.enabled());
        assertConflict(() -> users.updateUser(second, readerId, assigned.revision(), "不应覆盖", !assigned.enabled()));
        var edited = users.user(operator, readerId);
        assertEquals("第一次修改", edited.name());
        assertEquals(assigned.enabled(), edited.enabled());
        assertEquals(assigned.revision() + 1, edited.revision());

        var originalRole = roles.role(operator, roleA);
        roles.updateRole(operator, roleA, originalRole.revision(), "首次权限修改", true, Set.of("role:read"), originalRole.count());
        assertConflict(() -> roles.updateRole(second, roleA, originalRole.revision(), "不应覆盖", true,
                Set.of("user:manage"), originalRole.count()));
        var updatedRole = roles.role(operator, roleA);
        assertEquals(Set.of("role:read"), updatedRole.permissions());
        assertEquals("首次权限修改", updatedRole.name());
        assertEquals(originalRole.revision() + 1, updatedRole.revision());

        // AC-08：两个已登录管理员同步发起交叉停用，读取实际最终状态。
        int writesBefore = auditCount(db);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> runCompetingWrite(ready, start,
                    () -> users.updateUser(operator, secondAdmin.id(), secondAdmin.revision(), "由一号停用", false)));
            var b = pool.submit(() -> runCompetingWrite(ready, start,
                    () -> users.updateUser(second, owner.id(), owner.revision(), "由二号停用", false)));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "并发任务未就绪");
            start.countDown();
            int firstResult = a.get(15, TimeUnit.SECONDS);
            int secondResult = b.get(15, TimeUnit.SECONDS);
            assertEquals(Set.of(204, 401), Set.of(firstResult, secondResult));
            boolean ownerSurvived = firstResult == 204;
            var saved = ownerSurvived ? owner : secondAdmin;
            var disabled = ownerSurvived ? secondAdmin : owner;
            String survivorSession = ownerSurvived ? operator : second;
            assertEquals(saved, users.user(survivorSession, saved.id()));
            var changed = users.user(survivorSession, disabled.id());
            assertFalse(changed.enabled());
            assertEquals(disabled.revision() + 1, changed.revision());
            assertEquals(disabled.roles(), changed.roles());
            assertEquals(401, assertThrows(ApiException.class,
                    () -> identity.current(ownerSurvived ? second : operator)).status);
            String survivingPassword = ownerSurvived ? operatorPassword : password;
            var freshLogin = identity.login("survivor", saved.login(), survivingPassword);
            assertEquals(200, freshLogin.status());
            assertTrue(identity.current(freshLogin.token()).admin());
            assertEquals(writesBefore + 1, auditCount(db));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private static void assertConflict(Runnable write) {
        var conflict = assertThrows(ApiException.class, write::run);
        assertEquals(409, conflict.status);
        assertEquals("CONFLICT", conflict.code);
    }

    private static int runCompetingWrite(CountDownLatch ready, CountDownLatch start, Runnable write) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "并发任务未收到开始信号");
        try {
            write.run();
            return 204;
        } catch (ApiException ex) {
            return ex.status;
        }
    }

    private static int auditCount(JdbcTemplate db) {
        return db.queryForObject("SELECT COUNT(*) FROM audit_event WHERE operation='UPDATE_USER'", Integer.class);
    }
}
