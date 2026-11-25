package dev.unified.auth;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PreparedPasswordTest {
@TempDir Path temp;
@Test void preparationDoesNotCreateAccountAndCompletingKeepsBothCredentials() throws Exception {
DualCredentialStore store=new DualCredentialStore(temp.resolve("credentials.properties"),100000);
String prepared=store.preparePassword("example-password");
assertFalse(store.exists("player"));
assertThrows(IllegalArgumentException.class,()->store.registerPrepared("player","12",prepared));
assertFalse(store.exists("player"));
assertTrue(store.registerPrepared("player","1234",prepared));
assertTrue(store.verifyPassword("player","example-password"));
assertTrue(store.verifyPin("player","1234"));
assertFalse(store.verifyPin("player","0000"));
assertFalse(store.verifyPassword("player","wrong-password"));
assertFalse(store.registerPrepared("player","9876",prepared));
}
}
