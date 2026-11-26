package pvp;
/** Pure resource policy for a selected practice session. */
public final class SupportPlan {
public enum Need { HEAL, SPEED, STRENGTH, NONE }
public static boolean safeDistance(double distance, boolean grounded) {
return grounded && Double.isFinite(distance) && distance>=5;
}
public static boolean needsHeal(double hp,double maxHp) {
return Double.isFinite(hp) && Double.isFinite(maxHp) && maxHp>0 && hp>0 && hp<=maxHp*.60;
}
public static boolean needsBuff(int activeDuration,int activeAmplifier,int offeredAmplifier) {
return activeDuration==0 || activeDuration>0 && activeDuration<=60 && offeredAmplifier>=activeAmplifier;
}
private SupportPlan() {}
}
