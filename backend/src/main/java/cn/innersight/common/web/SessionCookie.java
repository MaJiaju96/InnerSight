package cn.innersight.common.web;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookie {
    public static final String NAME = "innersight_session";
    private final boolean secure;

    public SessionCookie(@Value("${innersight.secure-cookie}") boolean secure) { this.secure = secure; }

    public String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure)
                .sameSite("Strict").path("/api").maxAge(maxAge).build().toString();
    }
}
