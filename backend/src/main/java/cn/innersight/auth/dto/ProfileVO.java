package cn.innersight.auth.dto;

import cn.innersight.role.dto.RoleRefVO;
import java.util.List;
import java.util.Set;

public record ProfileVO(long id, String login, String name, boolean temporary, boolean admin,
                        List<RoleRefVO> roles, Set<String> permissions) {}
