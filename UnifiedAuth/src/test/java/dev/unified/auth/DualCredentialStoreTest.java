package dev.unified.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class DualCredentialStoreTest {
@TempDir Path temp;

@Test void acceptsFourDigitPinAndLongPassword() throws Exception {
DualCredentialStore store=new DualCredentialStore(temp.resolve("credentials.properties"),150000);
assertTrue(store.register("alex","0149","a long passphrase of mine"));
assertTrue(store.verifyPin("alex","0149"));
assertTrue(store.verifyPassword("alex","a long passphrase of mine"));
}

@Test void wrongPinAndPasswordAreDeniedIndependently() throws Exception {
DualCredentialStore store=new DualCredentialStore(temp.resolve("credentials.properties"),150000);
store.register("alex","1234","another long passphrase");
assertFalse(store.verifyPin("alex","9999"));
assertFalse(store.verifyPassword("alex","another long passphrase1"));
assertFalse(store.verifyPin("other","1234"));
}

@Test void existingAccountCannotBeOverwritten() throws Exception {
DualCredentialStore store=new DualCredentialStore(temp.resolve("credentials.properties"),150000);
assertTrue(store.register("alex","1234","first passphrase"));
assertFalse(store.register("alex","5678","second passphrase"));
assertTrue(store.verifyPin("alex","1234"));
assertFalse(store.verifyPin("alex","5678"));
}

@Test void credentialsPersistButNotAsPlaintext() throws Exception {
Path path=temp.resolve("credentials.properties");
DualCredentialStore store=new DualCredentialStore(path,150000);
store.register("alex","9876","very private long password");
String raw=Files.readString(path);
assertFalse(raw.contains("very private long password"));
assertFalse(raw.contains("9876"));
DualCredentialStore reopened=new DualCredentialStore(path,150000);
assertTrue(reopened.verifyPassword("alex","very private long password"));
assertTrue(reopened.verifyPin("alex","9876"));
}

@Test void shortPasswordsAndIncorrectPinsFailRegistration() throws Exception {
DualCredentialStore store=new DualCredentialStore(temp.resolve("credentials.properties"),150000);
assertThrows(IllegalArgumentException.class,()->store.register("alex","123","good-enough-password"));
assertThrows(IllegalArgumentException.class,()->store.register("alex","1234","short"));
assertEquals(0,store.count());
}
}
