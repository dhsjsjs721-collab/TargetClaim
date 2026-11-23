package dev.unified.auth;

/**
 * Login policy: a remembered IP may only skip the PASSWORD after
 * the 4-digit PIN was cryptographically verified.
 * IP trust never grants direct entry to the Minecraft world.
 */
final class LoginDecision {
private LoginDecision(){}
static boolean grant(boolean pinValid,boolean passwordValid,boolean rememberedIp){
return pinValid && (rememberedIp || passwordValid);
}
}
