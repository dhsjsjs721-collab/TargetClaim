package dev.unified.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RememberIpPolicyTest {
@Test void directOfflineNeverSkipsPassword(){
assertFalse(RememberIpPolicy.maySkipPassword(false,false,true));
assertFalse(RememberIpPolicy.maySkipPassword(false,false,false));
}
@Test void verifiedIdentityStillRequiresExplicitServerSetting(){
assertTrue(RememberIpPolicy.maySkipPassword(true,false,true));
assertTrue(RememberIpPolicy.maySkipPassword(false,true,true));
assertFalse(RememberIpPolicy.maySkipPassword(true,false,false));
assertFalse(RememberIpPolicy.maySkipPassword(false,true,false));
}
}
