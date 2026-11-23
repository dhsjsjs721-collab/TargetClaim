package dev.unified.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LoginDecisionTest {
@Test void rememberedIpCannotBypassWrongPin(){
assertFalse(LoginDecision.grant(false,false,true));
assertFalse(LoginDecision.grant(false,true,true));
}
@Test void trustedIpSkipsOnlyPassword(){
assertTrue(LoginDecision.grant(true,false,true));
}
@Test void untrustedIpRequiresBothSecrets(){
assertFalse(LoginDecision.grant(true,false,false));
assertFalse(LoginDecision.grant(false,true,false));
assertTrue(LoginDecision.grant(true,true,false));
}
@Test void validPasswordAndPinWorksWithOrWithoutTrust(){
assertTrue(LoginDecision.grant(true,true,false));
assertTrue(LoginDecision.grant(true,true,true));
}
}
