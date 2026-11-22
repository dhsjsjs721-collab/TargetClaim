package dev.unified.antiesp;

/** Pure policy for the optional no-skylight distant-entity visibility cap. */
final class StrictDistancePolicy {
private StrictDistancePolicy(){}
static boolean hide(boolean enabled,boolean isLiving,boolean underground,double squaredDistance,double cutoffSquared){
return enabled && isLiving && underground
&& Double.isFinite(squaredDistance) && squaredDistance>=cutoffSquared;
}
}
