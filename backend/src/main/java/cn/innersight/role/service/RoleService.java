package cn.innersight.role.service;

import cn.innersight.auth.service.IdentityService;
import cn.innersight.common.dto.PageVO;
import cn.innersight.common.error.ApiException;
import cn.innersight.role.dto.RoleRefVO;
import cn.innersight.role.dto.RoleVO;
import cn.innersight.role.repository.RoleRepository;
import static cn.innersight.auth.service.IdentityRules.*;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    private final IdentityService identity;
    private final RoleRepository repository;

    public RoleService(IdentityService identity, RoleRepository repository) {
        this.identity = identity;
        this.repository = repository;
    }

    @Transactional
    public PageVO<RoleVO> roles(String token, String search, Boolean enabled, int page, int size) {
        identity.current(token).require(ROLE_READ);
        return repository.roles(search, enabled, page, size);
    }

    @Transactional
    public RoleVO role(String token, long id) {
        identity.current(token).require(ROLE_READ);
        return repository.role(id);
    }
    @Transactional
    public List<RoleRefVO> roleOptions(String token) {
        identity.current(token).administrator();
        return repository.options();
    }
    @Transactional
    public Set<String> catalog(String token) {
        identity.current(token).require(ROLE_READ);
        return CATALOG;
    }
    @Transactional
    public void createRole(String token, String code, String name, Set<String> selection) {
        var actor = identity.writer(token);
        actor.administrator();
        String canonical = roleCode(code);
        if (ADMIN.equals(canonical)) throw ApiException.denied();
        var permissions = permissions(selection);
        long id = repository.insert(canonical, name(name), permissions);
        identity.audit(actor.id(), "CREATE_ROLE", id);
    }
    @Transactional
    public void updateRole(String token, long id, long revision, String name, boolean enabled, Set<String> selection, long expectedCount) {
        var actor = identity.writer(token);
        actor.administrator();
        var target = repository.findRole(id);
        if (ADMIN.equals(target.code())) throw ApiException.denied();
        version(target.revision(), revision);
        if (target.enabled() != enabled && target.count() != expectedCount) throw ApiException.conflict();
        var permissions = permissions(selection);
        repository.update(id, name(name), enabled, permissions);
        identity.audit(actor.id(), "UPDATE_ROLE", id);
    }

    public boolean enabled(long id) { return repository.findRole(id).enabled(); }
    private void version(long actual, long expected) { if (actual != expected) throw ApiException.conflict(); }
}
