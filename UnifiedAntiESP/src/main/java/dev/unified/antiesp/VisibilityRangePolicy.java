package dev.unified.antiesp;

/** Never exempt nearby entities: nearby walls must block ESP as well. */
final class VisibilityRangePolicy {
private VisibilityRangePolicy(){}
static boolean within(double squaredDistance,double maximumSquared){
return Double.isFinite(squaredDistance)
&& Double.isFinite(maximumSquared)
&& squaredDistance>=0 && squaredDistance<=maximumSquared;
}
}
