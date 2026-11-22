package dev.unified.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class RememberedIpStore {
private static final int SECRET_BYTES = 32;
private final Path file;
private final byte[] key;
private final Properties trusted = new Properties();
private final long durationMs;

RememberedIpStore(Path directory,int days) throws IOException {
Files.createDirectories(directory);
file=directory.resolve("trusted-ips.properties");
durationMs=Math.max(1,Math.min(30,days))*86400000L;
Path keyFile=directory.resolve("remember-key.bin");
if(Files.exists(keyFile)){
key=Files.readAllBytes(keyFile);
if(key.length!=SECRET_BYTES)throw new IOException("Invalid remember-key.bin; trust is disabled");
}else{
byte[] generated=new byte[SECRET_BYTES];
new SecureRandom().nextBytes(generated);
atomicWrite(keyFile,generated);
key=generated;
}
if(Files.exists(file)) {
try(InputStream in=Files.newInputStream(file)){trusted.load(in);}
catch(IllegalArgumentException corrupt){throw new IOException("Corrupt trusted IP database",corrupt);}
}
if(trusted.size()>50000)throw new IOException("Trusted IP database unexpectedly large");
}

synchronized boolean isTrusted(String name,UUID uuid,String ip,long now) {
String value=trusted.getProperty(identity(name,uuid));
if(value==null)return false;
try{
String[] parts=value.split(":",2);
if(parts.length!=2)return false;
long expires=Long.parseLong(parts[0]);
if(expires<=now || expires>now+31L*86400000L)return false;
byte[] saved=Base64.getDecoder().decode(parts[1]);
if(saved.length!=32)return false;
return MessageDigest.isEqual(saved, fingerprint(name,uuid,ip));
}catch(Exception invalid){return false;}
}

synchronized long trustedUntil(String name,UUID uuid,String ip,long now) {
if(!isTrusted(name,uuid,ip,now))return 0;
try{return Long.parseLong(trusted.getProperty(identity(name,uuid)).split(":",2)[0]);}
catch(Exception invalid){return 0;}
}

synchronized void trust(String name,UUID uuid,String ip,long now) throws IOException {
if(ip==null || ip.isBlank())throw new IOException("Client IP unavailable");
String id=identity(name,uuid);
String previous=trusted.getProperty(id);
String value=(now+durationMs)+":"+Base64.getEncoder().encodeToString(fingerprint(name,uuid,ip));
trusted.setProperty(id,value);
try{persist();}catch(IOException error){
if(previous==null)trusted.remove(id);else trusted.setProperty(id,previous);
throw error;
}
}

synchronized boolean forget(String name,UUID uuid) throws IOException {
String id=identity(name,uuid);
String previous=(String)trusted.remove(id);
if(previous==null)return false;
try{persist();}catch(IOException error){trusted.setProperty(id,previous);throw error;}
return true;
}

private static String identity(String name,UUID uuid){return uuid+"."+name;}
private byte[] fingerprint(String name,UUID uuid,String ip) {
try{
Mac mac=Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(key,"HmacSHA256"));
String value=identity(name,uuid)+"\u0000"+ip;
return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
}catch(Exception impossible){throw new IllegalStateException("HmacSHA256 not available",impossible);}
}

private void persist() throws IOException {
Path tmp=Files.createTempFile(file.getParent(),"trusted-ips-", ".tmp");
try{
try(OutputStream out=Files.newOutputStream(tmp)){
trusted.store(out,"UnifiedAuth optional trusted network records (no raw IPs). Protect with server files.");
}
try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
catch(AtomicMoveNotSupportedException unavailable){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
}finally{Files.deleteIfExists(tmp);}
}

private static void atomicWrite(Path file,byte[] bytes) throws IOException {
Path tmp=Files.createTempFile(file.getParent(),"remember-key-", ".tmp");
try {
Files.write(tmp,bytes);
try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
catch(AtomicMoveNotSupportedException unavailable){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
}finally{Files.deleteIfExists(tmp);}
}
}
