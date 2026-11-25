package dev.unified.auth;

final class RememberIpPolicy {
private RememberIpPolicy(){}

static boolean maySkipPassword(boolean onlineMode,boolean verifiedProxy,boolean allowPasswordSkip){
return allowPasswordSkip && (onlineMode || verifiedProxy);
}
}
