package cn.innersight.user.service;

import cn.innersight.auth.service.IdentityService;
import cn.innersight.common.dto.PageVO;
import cn.innersight.common.error.ApiException;
import cn.innersight.role.service.RoleService;
import cn.innersight.user.dto.UserVO;
import cn.innersight.user.repository.UserRepository;
import static cn.innersight.auth.service.IdentityRules.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final IdentityService identity;
    private final UserRepository repository;
    private final RoleService roles;

    public UserService(IdentityService identity, UserRepository repository, RoleService roles) {
        this.identity = identity;
        this.repository = repository;
        this.roles = roles;
    }

    @Transactional
    public PageVO<UserVO> users(String token, String search, Boolean enabled, int page, int size) {
        identity.current(token).require(USER_READ);
        return repository.users(search, enabled, page, size);
    }

    @Transactional
    public UserVO user(String token, long id) {
        identity.current(token).require(USER_READ);
        var u = identity.find(id);
        return new UserVO(id,u.login(),u.name(),u.enabled(),u.revision(),repository.rolesFor(List.of(id)).getOrDefault(id,List.of()));
    }

    @Transactional
    public void createUser(String token, String login, String name, String temporary) {
        var actor = identity.writer(token);
        actor.require(USER_MANAGE);
        String canonical = newLogin(login);
        long id = identity.createAccount(canonical, name(name), temporary);
        identity.audit(actor.id(), "CREATE_USER", id);
    }
    @Transactional
    public void updateUser(String token, long id, long revision, String name, boolean enabled) {
        var actor = identity.writer(token);
        actor.require(USER_MANAGE);
        var target = identity.find(id);
        boolean admin = hasAdmin(id);
        if ((!actor.admin() && admin) || (id == actor.id() && !enabled)) throw ApiException.denied();
        version(target.revision(), revision);
        if (admin && target.enabled() && !enabled) retainAdmin(id);
        repository.update(id, name(name), enabled);
        if (!enabled) identity.revoke(id);
        identity.audit(actor.id(), "UPDATE_USER", id);
    }
    @Transactional
    public void reset(String token, long id, long revision, String temporary) {
        var actor = identity.writer(token);
        actor.administrator();
        if (actor.id() == id) throw ApiException.denied();
        var target = identity.find(id);
        version(target.revision(), revision);
        identity.replacePassword(target, temporary, true);
        identity.audit(actor.id(), "RESET_PASSWORD", id);
    }
    @Transactional
    public void assign(String token, long id, long revision, Set<Long> selection) {
        var actor = identity.writer(token);
        actor.administrator();
        version(identity.find(id).revision(), revision);
        var existing = repository.rolesFor(List.of(id)).getOrDefault(id,List.of());
        Set<Long> current = new HashSet<>();
        boolean removesAdmin = false;
        for (var role : existing) {
            current.add(role.id());
            if (ADMIN.equals(role.code()) && !selection.contains(role.id())) removesAdmin = true;
        }
        if (removesAdmin) {
            if (actor.id() == id) throw ApiException.denied();
            retainAdmin(id);
        }
        for (Long added : selection) {
            if (added == null) throw ApiException.invalid("角色选择不正确。");
            if (!current.contains(added) && !roles.enabled(added)) throw ApiException.conflict();
        }
        repository.assign(id, selection);
        identity.audit(actor.id(), "ASSIGN_ROLES", id);
    }
    private boolean hasAdmin(long id) {
        return repository.rolesFor(List.of(id)).getOrDefault(id, List.of()).stream().anyMatch(r -> ADMIN.equals(r.code()));
    }
    private void retainAdmin(long excluded) {
        if (repository.otherEnabledAdmins(excluded) == 0)
            throw new ApiException(409, "ADMIN_REQUIRED", "必须至少保留一个启用的管理员账号。");
    }
    private void version(long actual, long expected) { if (actual != expected) throw ApiException.conflict(); }
}
