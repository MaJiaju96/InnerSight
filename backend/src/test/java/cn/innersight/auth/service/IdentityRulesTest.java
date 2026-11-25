package cn.innersight.auth.service;

import cn.innersight.common.error.ApiException;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityRulesTest {
    @Test void loginAndRoleCodesNormalize() {
        assertEquals("a.b_1", IdentityRules.newLogin("  A.B_1 "));
        assertEquals("USER_READ", IdentityRules.roleCode("user_read"));
        assertThrows(ApiException.class, () -> IdentityRules.newLogin("foo@bar"));
    }
    @Test void passwordPreservesWhitespaceAndCountsCodePoints() {
        var value = "  " + "界".repeat(12) + " ";
        assertEquals(value, IdentityRules.password(value));
        assertDoesNotThrow(() -> IdentityRules.password("😀".repeat(128)));
        assertThrows(ApiException.class, () -> IdentityRules.password("😀".repeat(129)));
    }
    @Test void managingUsersImpliesReadAndCannotGrantAdmin() {
        assertEquals(Set.of("user:read","user:manage"), IdentityRules.permissions(Set.of("user:manage")));
        assertThrows(ApiException.class, () -> IdentityRules.permissions(Set.of("ADMIN")));
    }
    @Test void throttleWindowIsRolling() {
        assertEquals(List.of(401L, 900L, 1000L), IdentityRules.failures(List.of(400L, 401L, 900L),1000L));
    }
    @Test void sessionExpiryIsInclusiveAtBothBoundaries() {
        assertFalse(IdentityRules.expired(0,27000,28799));
        assertTrue(IdentityRules.expired(0,28500,28800));
        assertTrue(IdentityRules.expired(0,100,1900));
    }
}
