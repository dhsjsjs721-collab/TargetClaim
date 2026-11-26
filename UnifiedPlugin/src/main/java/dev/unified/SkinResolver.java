package dev.unified;
import com.google.gson.*;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class SkinResolver {
private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(7)).followRedirects(HttpClient.Redirect.NEVER).build();
private final String apiKey;
SkinResolver(String key){apiKey=key==null?"":key.strip();}
static String source(String input){
if(input==null||input.length()>1800)return null;
if(input.matches("[A-Za-z0-9_]{1,16}"))return input;
try{
URI u=URI.create(input);String h=u.getHost();if(h==null||!"https".equalsIgnoreCase(u.getScheme())||u.getRawUserInfo()!=null||u.getRawFragment()!=null||(u.getPort()!=-1&&u.getPort()!=443))return null;
h=h.toLowerCase(Locale.ROOT);String path=u.getPath();
if(!h.matches("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,63}")||h.endsWith(".local")||h.endsWith(".internal")||h.endsWith(".localhost"))return null;
if(h.equals("namemc.com")||h.endsWith(".namemc.com")&&!h.equals("texture.namemc.com")){
if(path.matches("/profile/[A-Za-z0-9_]{1,16}(\\.[0-9]+)?/?"))return path.substring(9).replaceFirst("[./].*$","");
if(path.matches("/skin/[a-fA-F0-9]{16}/?")){String id=path.substring(6,22).toLowerCase(Locale.ROOT);return "https://texture.namemc.com/"+id.substring(0,2)+"/"+id.substring(2,4)+"/"+id+".png";}
if(!path.toLowerCase(Locale.ROOT).endsWith(".png"))return null;
}
if(Set.of("mineskin.org","www.mineskin.org").contains(h)&&path.matches("/skins?/[a-fA-F0-9-]{8,36}/?"))return "mineskin:"+path.replaceFirst("^/skins?/","").replaceFirst("/$","");
return u.toASCIIString();
}catch(Exception e){return null;}
}
private JsonObject request(String url,JsonObject body,boolean mine)throws Exception{
HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(18)).header("User-Agent","LandOfLosers/1.1.22 (Minecraft server skin resolver)").header("Accept","application/json");
if(mine&&!apiKey.isEmpty())b.header("Authorization","Bearer "+apiKey);
if(body==null)b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
HttpResponse<InputStream> r=http.send(b.build(),HttpResponse.BodyHandlers.ofInputStream());
try(InputStream in=r.body()){byte[] data=in.readNBytes(262145);if(data.length>262144)throw new IOException("Слишком большой ответ.");if(r.statusCode()==429)throw new IOException("Сервис скинов ограничил запросы. Попробуй позже.");if(r.statusCode()==401||r.statusCode()==403)throw new IOException(mine?"MineSkin требует доступ/API-ключ.":"Mojang отказал в запросе.");if(r.statusCode()/100!=2||data.length==0)throw new IOException("Скин не найден или сервис недоступен (HTTP "+r.statusCode()+").");return JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();}
}
private static ProfileProperty property(JsonObject o)throws IOException{
String value=o.get("value").getAsString(),sig=o.get("signature").getAsString();
if(value.length()>16384||sig.length()>4096||sig.isBlank())throw new IOException("Нет подписанной текстуры.");
JsonObject decoded=JsonParser.parseString(new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8)).getAsJsonObject();
String url=decoded.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
URI u=URI.create(url);if(!"textures.minecraft.net".equalsIgnoreCase(u.getHost())||!u.getPath().matches("/texture/[a-fA-F0-9]{32,128}"))throw new IOException("Некорректная текстура Mojang.");
return new ProfileProperty("textures",value,sig);
}
private static ProfileProperty mineProperty(JsonObject o)throws IOException{
return property(o.getAsJsonObject("skin").getAsJsonObject("texture").getAsJsonObject("data"));
}
ProfileProperty resolve(String src,String variant)throws Exception{
if(src.matches("[A-Za-z0-9_]{1,16}")){
JsonObject name=request("https://api.mojang.com/users/profiles/minecraft/"+src,null,false);String id=name.get("id").getAsString();if(!id.matches("[a-fA-F0-9]{32}"))throw new IOException("Некорректный профиль.");
JsonObject session=request("https://sessionserver.mojang.com/session/minecraft/profile/"+id+"?unsigned=false",null,false);
for(JsonElement e:session.getAsJsonArray("properties")){JsonObject o=e.getAsJsonObject();if("textures".equals(o.get("name").getAsString()))return property(o);}
throw new IOException("У игрока нет подписанного скина.");
}
if(src.startsWith("mineskin:"))return mineProperty(request("https://api.mineskin.org/v2/skins/"+src.substring(9),null,true));
URI uri=URI.create(src);for(InetAddress ip:InetAddress.getAllByName(uri.getHost()))if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress())throw new IOException("Требуется публичный адрес.");
// External images are downloaded by MineSkin, never by the Minecraft server.
JsonObject body=new JsonObject();body.addProperty("url",src);body.addProperty("variant",variant);body.addProperty("visibility","unlisted");
JsonObject response=request("https://api.mineskin.org/v2/queue",body,true);
if(response.has("skin"))return mineProperty(response);
String job=response.getAsJsonObject("job").get("id").getAsString();if(!job.matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("Некорректный ID MineSkin.");
long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
while(System.nanoTime()<end){
Thread.sleep(3000);response=request("https://api.mineskin.org/v2/queue/"+job,null,true);
if(response.has("skin"))return mineProperty(response);
JsonObject j=response.getAsJsonObject("job");String status=j.get("status").getAsString();
if(status.equals("failed"))throw new IOException("MineSkin не смог обработать картинку. Нужен PNG 64×64 или 64×32.");
if(status.equals("completed")&&j.has("result")){String id=j.get("result").getAsString();if(id.matches("[A-Za-z0-9_-]{1,100}"))return mineProperty(request("https://api.mineskin.org/v2/skins/"+id,null,true));}
}
throw new IOException("MineSkin не успел подготовить скин.");
}
}
