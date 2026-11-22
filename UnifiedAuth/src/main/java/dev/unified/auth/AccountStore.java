package dev.unified.auth;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class AccountStore {
private final Path file;
private final Properties records = new Properties();
private final SecureRandom random = new SecureRandom();
private final int iterations;
AccountStore(Path file,int iterations) throws IOException {
this.file=file;
this.iterations=iterations;
Files.createDirectories(file.getParent());
if(Files.exists(file))try(InputStream in=Files.newInputStream(file)){records.load(in);}
}
synchronized boolean exists(String normalizedName){return records.containsKey(normalizedName);}
synchronized int count(){return records.size();}
synchronized boolean authenticate(String name,String pin){
String record=records.getProperty(name);
if(record==null)return false;
try {
String[] parts=record.split(":",3);
if(parts.length!=3)return false;
int rounds=Integer.parseInt(parts[0]);
if(rounds<100000 || rounds>800000)return false;
byte[] salt=Base64.getDecoder().decode(parts[1]);
byte[] expected=Base64.getDecoder().decode(parts[2]);
if(salt.length!=16 || expected.length!=32)return false;
byte[] actual=derive(pin,salt,rounds);
boolean correct=MessageDigest.isEqual(expected,actual);
Arrays.fill(actual,(byte)0);
return correct;
} catch(Exception e){return false;}
}
synchronized boolean register(String name,String pin) throws Exception {
if(records.containsKey(name))return false;
byte[] salt=new byte[16];
random.nextBytes(salt);
byte[] hash=derive(pin,salt,iterations);
records.setProperty(name,iterations+":"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(hash));
Arrays.fill(hash,(byte)0);
try {persist();} catch(Exception ex){records.remove(name);throw ex;}
return true;
}
private void persist() throws IOException {
Path tmp=Files.createTempFile(file.getParent(),"accounts-", ".tmp");
try {
try(OutputStream out=Files.newOutputStream(tmp)){records.store(out,"UnifiedAuth PBKDF2 account records. Protect this file and take secure backups.");}
try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
}finally{Files.deleteIfExists(tmp);}
}
private static byte[] derive(String pin,byte[] salt,int rounds) throws Exception {
char[] chars=pin.toCharArray();
PBEKeySpec spec=new PBEKeySpec(chars,salt,rounds,256);
try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
finally{spec.clearPassword();Arrays.fill(chars,'\0');}
}
}
