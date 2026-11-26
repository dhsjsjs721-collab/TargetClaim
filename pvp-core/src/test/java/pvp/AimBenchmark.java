package pvp;
import java.util.Locale;
/** Deterministic geometry-only experiment. Not Minecraft physics or a win-rate test. */
public final class AimBenchmark {
private static final CombatSettings SETTINGS=new CombatSettings(true,true,.55,.4,360);
private static final AimModel.Turn ZERO=new AimModel.Turn(0,0);
private static final int TICKS=240;
private interface Controller { AimModel.Turn step(AimModel.Vec eye,AimModel.Angles view,AimModel.Box box,AimModel.Turn previous,double motion); }
private record Sample(AimModel.Vec eye,AimModel.Box box) {}
private record Result(int aligned,int first,int longestMiss,double rmsDelta,double peakDelta,double peakTurn) {}
private static AimModel.Box box(double x,double y,double z) { return new AimModel.Box(new AimModel.Vec(x-.3,y,z-.3),new AimModel.Vec(x+.3,y+1.8,z+.3)); }
private static Sample sample(int scenario,int tick) {
double x=0,y=0,z=2.8,ex=0,ey=1.62,ez=0;
switch(scenario) {
case 1 -> x=1.8*Math.sin(tick*.13);
case 2 -> { x=2.2*Math.sin(tick*.13); z=2.2*Math.cos(tick*.13); }
case 3 -> { int phase=tick%40; x=phase<20?-1.7+phase*.17:1.7-(phase-20)*.17; }
case 4 -> { y=.9*Math.abs(Math.sin(tick*.22)); ey+=.35*Math.sin(tick*.19); }
case 5 -> { int phase=tick%60; x=phase<20?0:phase<40?1.8:-1.8; }
case 6 -> { x=.75*Math.sin(tick*.29); z=1.15+.22*Math.sin(tick*.2); ex=.25*Math.sin(tick*.11); }
case 7 -> { x=1.4*Math.sin(tick*.1); ex=1.1*Math.sin(tick*.14); ez=.8*Math.sin(tick*.09); }
default -> {}
}
return new Sample(new AimModel.Vec(ex,ey,ez),box(x,y,z));
}
private static double clamp(double n,double a,double b) { return Math.max(a,Math.min(b,n)); }
// Experimental camera candidate, benchmark-only. Never invoked by the game.
private static AimModel.Turn candidate(AimModel.Vec eye,AimModel.Angles view,AimModel.Box box,AimModel.Turn previous,double motion) {
if(AimModel.intersects(eye,view,box)) return ZERO;
var desired=AimModel.lockDirection(eye,view,box);
double dy=AimModel.wrap(desired.yaw()-view.yaw()),dp=desired.pitch()-view.pitch();
return new AimModel.Turn(axis(dy,previous.yaw(),26,8),axis(dp,previous.pitch(),17,5));
}
private static double axis(double error,double previous,double limit,double acceleration) {
double wanted=Math.copySign(Math.min(Math.abs(error),Math.min(limit,Math.sqrt(2*acceleration*Math.abs(error)))),error);
double next=previous+clamp(wanted-previous,-acceleration,acceleration);
if(Math.signum(next)!=Math.signum(error)) return 0;
return Math.copySign(Math.min(Math.abs(error),Math.abs(next)),error);
}
private static Result run(int scenario,Controller controller) {
var view=new AimModel.Angles(scenario==0?155:0,scenario==0?25:0);
var previous=ZERO;
AimModel.Angles previousCenter=null;
int aligned=0,first=-1,miss=0,longestMiss=0;
double sum=0,peak=0,peakTurn=0;
for(int tick=0;tick<TICKS;tick++) {
var frame=sample(scenario,tick);
var center=AimModel.direction(frame.eye(),AimModel.point(frame.box(),.65));
double motion=previousCenter==null?0:AimModel.error(previousCenter,center);
previousCenter=center;
boolean before=AimModel.intersects(frame.eye(),view,frame.box());
var turn=controller.step(frame.eye(),view,frame.box(),previous,motion);
if(!Double.isFinite(turn.yaw()) || !Double.isFinite(turn.pitch()) || before && (turn.yaw()!=0 || turn.pitch()!=0)) throw new AssertionError("Invalid correction");
view=new AimModel.Angles(AimModel.wrap(view.yaw()+turn.yaw()),clamp(view.pitch()+turn.pitch(),-90,90));
double delta=Math.hypot(turn.yaw()-previous.yaw(),turn.pitch()-previous.pitch());
sum+=delta*delta; peak=Math.max(peak,delta); peakTurn=Math.max(peakTurn,Math.hypot(turn.yaw(),turn.pitch()));
previous=turn;
if(AimModel.intersects(frame.eye(),view,frame.box())) { aligned++; if(first<0) first=tick+1; miss=0; }
else { miss++; longestMiss=Math.max(longestMiss,miss); }
}
return new Result(aligned,first,longestMiss,Math.sqrt(sum/TICKS),peak,peakTurn);
}
public static void main(String[] args) {
String[] names={"initial_turn","strafe","orbit","reversal","vertical","position_step","close","relative_motion"};
String[] variants={"direct_1.9.8","legacy_smooth","experimental","refined_1.10.0"};
Controller[] controllers={
(e,v,b,p,m)->AimModel.lockTurn(e,v,b,p,SETTINGS,m),
(e,v,b,p,m)->AimModel.intersects(e,v,b)?ZERO:AimModel.smoothTurn(v,AimModel.lockDirection(e,v,b),p,SETTINGS,m),
AimBenchmark::candidate,
(e,v,box,p,m)->AimModel.refinedTurn(e,v,box,p,SETTINGS,m)
};
System.out.println("AIM BENCHMARK | 20 Hz synthetic samples, no game physics");
System.out.println("scenario,controller,aligned_pct,first_tick,longest_miss,rms_delta,peak_delta,peak_turn");
for(int c=0;c<controllers.length;c++) {
int hits=0; double sum=0,peak=0,turn=0; int miss=0;
for(int k=0;k<names.length;k++) {
var result=run(k,controllers[c]);
if(k==0 && result.first()<0) throw new AssertionError("Static acquisition failed");
System.out.printf(Locale.ROOT,"%s,%s,%.2f,%d,%d,%.3f,%.3f,%.3f%n",names[k],variants[c],100.0*result.aligned()/TICKS,result.first(),result.longestMiss(),result.rmsDelta(),result.peakDelta(),result.peakTurn());
hits+=result.aligned(); sum+=result.rmsDelta()*result.rmsDelta(); peak=Math.max(peak,result.peakDelta()); turn=Math.max(turn,result.peakTurn()); miss=Math.max(miss,result.longestMiss());
}
double alignedPercent=100.0*hits/(TICKS*names.length),rms=Math.sqrt(sum/names.length);
System.out.printf(Locale.ROOT,"ALL,%s,%.2f,-1,%d,%.3f,%.3f,%.3f%n",variants[c],alignedPercent,miss,rms,peak,turn);
if(variants[c].equals("refined_1.10.0") && (alignedPercent<99.5 || miss>4 || rms>4.75 || peak>45)) throw new AssertionError("Existing aim benchmark regressed");
}
}
private AimBenchmark() {}
}
