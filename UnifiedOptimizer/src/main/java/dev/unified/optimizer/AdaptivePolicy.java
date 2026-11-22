package dev.unified.optimizer;

import java.util.Arrays;

final class AdaptivePolicy {
private final double overloadMspt;
private final double recoveryMspt;
private final double overloadTps;
private final double recoveryTps;
private final int requiredBad;
private final int requiredGood;
private final double alpha;
private final double[] history = new double[12];
private double ema = Double.NaN;
private double lastMspt = Double.NaN;
private double lastTps = Double.NaN;
private int badStreak, goodStreak, samples, next;

AdaptivePolicy(double overloadMspt, double recoveryMspt, double overloadTps, double recoveryTps, int requiredBad, int requiredGood, double alpha) {
this.overloadMspt=overloadMspt;
this.recoveryMspt=recoveryMspt;
this.overloadTps=overloadTps;
this.recoveryTps=recoveryTps;
this.requiredBad=requiredBad;
this.requiredGood=requiredGood;
this.alpha=alpha;
}

int observe(double mspt,double tps) {
if(!Double.isFinite(mspt) || !Double.isFinite(tps) || mspt<0 || tps<0 || tps>22) return 0;
lastMspt=mspt;
lastTps=tps;
ema=Double.isNaN(ema)?mspt:alpha*mspt+(1-alpha)*ema;
history[next]=mspt;
next=(next+1)%history.length;
if(samples<history.length)samples++;
// Require live tick pressure in addition to smoothed lag. One-off spikes
// and stale 1-minute TPS averages cannot force continuous reductions.
boolean bad=(ema>=overloadMspt && mspt>=recoveryMspt+2.0) ||
(tps<overloadTps && mspt>=overloadMspt-4.0);
boolean good=ema<=recoveryMspt && tps>=recoveryTps;
if(bad) {badStreak++;goodStreak=0;}
else if(good) {goodStreak++;badStreak=0;}
else {badStreak=0;goodStreak=0;}
if(badStreak>=requiredBad) return -1;
if(goodStreak>=requiredGood) return 1;
return 0;
}

void resetStreaks(){badStreak=0;goodStreak=0;}
double ema(){return ema;}
double lastMspt(){return lastMspt;}
double lastTps(){return lastTps;}
int badStreak(){return badStreak;}
int goodStreak(){return goodStreak;}
int samples(){return samples;}
double average(){
if(samples==0)return Double.NaN;
double sum=0;
for(int i=0;i<samples;i++)sum+=history[i];
return sum/samples;
}
double maximum(){
if(samples==0)return Double.NaN;
double max=Double.NEGATIVE_INFINITY;
for(int i=0;i<samples;i++)max=Math.max(max,history[i]);
return max;
}
}
