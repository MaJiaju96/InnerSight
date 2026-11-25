package cn.innersight.user.dto;
import cn.innersight.role.dto.RoleRefVO;
import java.util.List;
public record UserVO(long id, String login, String name, boolean enabled, long revision, List<RoleRefVO> roles) {}
