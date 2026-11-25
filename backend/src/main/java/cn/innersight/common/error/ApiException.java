package cn.innersight.common.error;

public final class ApiException extends RuntimeException {
    public final int status;
    public final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException denied() { return new ApiException(403, "FORBIDDEN", "没有权限执行此操作。"); }
    public static ApiException unauthenticated() { return new ApiException(401, "SESSION_EXPIRED", "请重新登录。"); }
    public static ApiException invalid(String message) { return new ApiException(400, "VALIDATION", message); }
    public static ApiException conflict() { return new ApiException(409, "CONFLICT", "数据已更新，请重新加载后确认。"); }
    public static ApiException missing() { return new ApiException(404, "NOT_FOUND", "记录不存在。"); }
}
