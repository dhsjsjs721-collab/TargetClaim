package dev.unified.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Independent two-secret credential database; legacy accounts.properties is left untouched. */
final class DualCredentialStore {
private final Path path;
private final int rounds;
private final Properties records = new Properties();
private final SecureRandom random = new SecureRandom();

DualCredentialStore(Path path,int rounds) throws IOException {
this.path=path;
this.rounds=rounds;
Files.createDirectories(path.getParent());
if(Files.exists(path)){
try(InputStream in=Files.newInputStream(path)){records.load(in);}
catch(IllegalArgumentException error){throw new IOException("Invalid v2 account file",error);}
}
if(records.size()>1000000)throw new IOException("Unexpectedly large credential database");
}

synchronized boolean exists(String name){return records.containsKey(name);}
synchronized int count(){return records.size();}

synchronized boolean verifyPin(String name,String pin){
if(pin==null || !pin.matches("[0-9]{4}"))return false;
String[] fields=fields(name);
return fields!=null && verify(pin,fields[0],fields[1],fields[2]);
}
synchronized boolean verifyPassword(String name,String password){
if(password==null || password.length()<10 || password.length()>64)return false;
String[] fields=fields(name);
return fields!=null && verify(password,fields[3],fields[4],fields[5]);
}

synchronized boolean register(String name,String pin,String password) throws Exception {
if(records.containsKey(name))return false;
if(pin==null || !pin.matches("[0-9]{4}"))throw new IllegalArgumentException("PIN must have 4 digits");
if(password==null || password.length()<10 || password.length()>64)throw new IllegalArgumentException("Password must be 10-64 characters");
String value=hash(pin)+":"+hash(password);
records.setProperty(name,value);
try{persist();}
catch(IOException io){records.remove(name);throw io;}
return true;
}

private String[] fields(String name){
String value=records.getProperty(name);
if(value==null)return null;
String[] parts=value.split(":",-1);
return parts.length==6?parts:null;
}
private String hash(String secret) throws Exception {
byte[] salt=new byte[16];
random.nextBytes(salt);
byte[] hashed=derive(secret,salt,rounds);
try{return rounds+":"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(hashed);}
finally{Arrays.fill(hashed,(byte)0);}
}
private boolean verify(String secret,String count,String saltString,String hashString){
try{
int iterations=Integer.parseInt(count);
if(iterations<100000 || iterations>800000)return false;
byte[] salt=Base64.getDecoder().decode(saltString);
byte[] expected=Base64.getDecoder().decode(hashString);
if(salt.length!=16 || expected.length!=32)return false;
byte[] actual=derive(secret,salt,iterations);
boolean verified=MessageDigest.isEqual(expected,actual);
Arrays.fill(actual,(byte)0);
return verified;
}catch(Exception invalid){return false;}
}
private static byte[] derive(String secret,byte[] salt,int iterations) throws Exception {
char[] chars=secret.toCharArray();
PBEKeySpec spec=new PBEKeySpec(chars,salt,iterations,256);
try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
finally{spec.clearPassword();Arrays.fill(chars,'\0');}
}
private void persist() throws IOException {
Path tmp=Files.createTempFile(path.getParent(),"credentials-v2-", ".tmp");
try{
try(OutputStream out=Files.newOutputStream(tmp)){
records.store(out,"UnifiedAuth two-stage PIN/password hashes. Never share this file.");
}
try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
}finally{Files.deleteIfExists(tmp);}
}
}
