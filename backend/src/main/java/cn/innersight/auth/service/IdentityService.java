package cn.innersight.auth.service;

import cn.innersight.auth.dto.ProfileVO;
import cn.innersight.auth.repository.IdentityRepository;
import cn.innersight.auth.repository.IdentityRepository.Account;
import cn.innersight.common.error.ApiException;
import cn.innersight.role.dto.RoleRefVO;

import static cn.innersight.auth.service.IdentityRules.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final IdentityRepository repository;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    IdentityService(IdentityRepository repository, PasswordEncoder encoder, Clock clock) {
        this.repository = repository;
        this.encoder = encoder;
        this.clock = clock;
        dummyHash = encoder.encode(token());
    }

    public record LoginResult(String token, boolean temporary, int status) {}
    @Transactional
    public boolean initialize(String login, String name, String temporaryPassword) {
        if (guard()) return false;
        String canonical = newLogin(login);
        long accountId = repository.initialize(canonical, name(name), encode(temporaryPassword));
        audit(null, "INITIALIZE", accountId);
        return true;
    }

    @Transactional
    public LoginResult login(String source, String rawLogin, String secret) {
        String canonical = IdentityRules.login(rawLogin);
        String bucket = hash(source + "\n" + canonical);
        long now = now();
        var throttle = repository.lockThrottle(bucket, now);
        if (throttle.blocked() > now) return new LoginResult(null, false, 429);
        Account account = repository.lockAccount(canonical.length() <= 32 && canonical.matches("[a-z0-9._-]+") ? canonical : "");
        boolean matches = encoder.matches(secret, account == null || !account.enabled() ? dummyHash : account.hash());
        if (account == null || !account.enabled() || !matches) {
            List<Long> previous = throttle.failures().isEmpty() ? List.of() :
                    Arrays.stream(throttle.failures().split(",")).map(Long::valueOf).toList();
            List<Long> failures = failures(previous, now);
            long blocked = failures.size() >= 5 ? now + 600 : 0;
            repository.updateThrottle(bucket, blocked == 0 ? failures.stream().map(String::valueOf).collect(Collectors.joining(",")) : "", blocked, now);
            audit(account == null ? null : account.id(), "LOGIN_FAILED", null);
            return new LoginResult(null, false, 401);
        }
        repository.clearThrottle(bucket);
        String token = token();
        repository.createSession(hash(token), account.id(), now);
        return new LoginResult(token, account.temporary(), 200);
    }

    @Transactional
    public AuthenticatedActor current(String token) { return new AuthenticatedActor(profile(session(token, true))); }

    @Transactional
    public void logout(String token) {
        if (validFormat(token)) repository.deleteSession(hash(token));
    }

    @Transactional
    public void changePassword(String token, String current, String next) {
        guard();
        Account account = session(token, false);
        if (!encoder.matches(current, account.hash())) throw ApiException.invalid("当前密码不正确。");
        replacePassword(account, next, false);
        audit(account.id(), "CHANGE_PASSWORD", account.id());
    }

    boolean guard() {
        return repository.lockInitialized();
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AuthenticatedActor writer(String token) {
        guard();
        return new AuthenticatedActor(profile(session(token, true)));
    }
    public Account find(long id) {
        Account result = repository.findAccount(id);
        if (result == null) throw ApiException.missing();
        return result;
    }
    private Account session(String token, boolean touch) {
        if (!validFormat(token)) throw ApiException.unauthenticated();
        long now = now();
        String digest = hash(token);
        var result = repository.findSession(digest);
        if (result == null || !result.account().enabled() || expired(result.started(), result.active(), now))
            throw ApiException.unauthenticated();
        if (touch && repository.touchSession(digest, now) == 0)
            throw ApiException.unauthenticated();
        return result.account();
    }
    private ProfileVO profile(Account account) {
        if (account.temporary()) return new ProfileVO(account.id(), account.login(), account.name(), true, false, List.of(), Set.of());
        List<RoleRefVO> roles = repository.activeRoles(account.id());
        boolean admin = roles.stream().anyMatch(r -> ADMIN.equals(r.code()));
        Set<String> permissions = admin ? CATALOG : repository.activePermissions(account.id());
        return new ProfileVO(account.id(), account.login(), account.name(), false, admin, roles, permissions);
    }
    public void replacePassword(Account account, String next, boolean temporary) {
        if (encoder.matches(password(next), account.hash())) throw ApiException.invalid("新密码不能与当前密码相同。");
        repository.updatePassword(account.id(), encoder.encode(next), temporary);
        revoke(account.id());
    }
    public void revoke(long accountId) { repository.revokeSessions(accountId); }
    public void audit(Long actor, String operation, Long target) {
        repository.audit(actor, operation, target);
    }
    public long createAccount(String login, String name, String temporary) {
        return repository.insertAccount(login, name, encode(temporary));
    }
    private String encode(String value) { return encoder.encode(password(value)); }
    long now() { return clock.instant().getEpochSecond(); }
    private String token() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private boolean validFormat(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
