package cn.innersight.auth.service;

import cn.innersight.auth.dto.ProfileVO;
import cn.innersight.common.error.ApiException;

public record AuthenticatedActor(ProfileVO profile) {
    public long id() { return profile.id(); }
    public boolean admin() { return profile.admin(); }
    public void require(String permission) {
        if (profile.temporary() || (!admin() && !profile.permissions().contains(permission))) throw ApiException.denied();
    }
    public void administrator() {
        if (profile.temporary() || !admin()) throw ApiException.denied();
    }
}
