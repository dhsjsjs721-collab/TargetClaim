package dev.unified.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class RememberedIpStoreTest {
@TempDir Path temp;

@Test void requiresExplicitOptInAndMatchesOnlyOriginalAccountAndIp() throws Exception {
RememberedIpStore store=new RememberedIpStore(temp,7);
UUID uuid=UUID.randomUUID();
long now=System.currentTimeMillis();
assertFalse(store.isTrusted("alex",uuid,"203.0.113.12",now));
store.trust("alex",uuid,"203.0.113.12",now);
assertTrue(store.isTrusted("alex",uuid,"203.0.113.12",now+1000));
assertFalse(store.isTrusted("alex",uuid,"203.0.113.13",now+1000));
assertFalse(store.isTrusted("other",uuid,"203.0.113.12",now+1000));
assertFalse(store.isTrusted("alex",UUID.randomUUID(),"203.0.113.12",now+1000));
}

@Test void trustExpiresAndDoesNotAutoExtend() throws Exception {
RememberedIpStore store=new RememberedIpStore(temp,1);
UUID uuid=UUID.randomUUID();
long now=System.currentTimeMillis();
store.trust("alex",uuid,"198.51.100.22",now);
assertTrue(store.isTrusted("alex",uuid,"198.51.100.22",now+1000));
assertFalse(store.isTrusted("alex",uuid,"198.51.100.22",now+86400001L));
}

@Test void trustIsPersistentAndRevocable() throws Exception {
UUID uuid=UUID.randomUUID();
long now=System.currentTimeMillis();
new RememberedIpStore(temp,3).trust("alex",uuid,"203.0.113.8",now);
RememberedIpStore restored=new RememberedIpStore(temp,3);
assertTrue(restored.isTrusted("alex",uuid,"203.0.113.8",now+1000));
assertTrue(restored.forget("alex",uuid));
assertFalse(restored.forget("alex",uuid));
assertFalse(new RememberedIpStore(temp,3).isTrusted("alex",uuid,"203.0.113.8",now+1000));
}

@Test void recordsContainNoPlaintextIp() throws Exception {
RememberedIpStore store=new RememberedIpStore(temp,7);
store.trust("alex",UUID.randomUUID(),"198.51.100.42",System.currentTimeMillis());
String text=Files.readString(temp.resolve("trusted-ips.properties"));
assertFalse(text.contains("198.51.100.42"));
assertEquals(32,Files.readAllBytes(temp.resolve("remember-key.bin")).length);
}

@Test void invalidKeyRefusesTrustInsteadOfRecreatingIt() throws Exception {
Files.writeString(temp.resolve("remember-key.bin"),"broken");
assertThrows(java.io.IOException.class,()->new RememberedIpStore(temp,7));
}

@Test void expiringDatabaseCannotBeReusedAfterKeyRotation() throws Exception {
UUID uuid=UUID.randomUUID();
long now=System.currentTimeMillis();
RememberedIpStore store=new RememberedIpStore(temp,7);
store.trust("alex",uuid,"203.0.113.100",now);
Files.delete(temp.resolve("remember-key.bin"));
assertFalse(new RememberedIpStore(temp,7).isTrusted("alex",uuid,"203.0.113.100",now+1000));
}
}
