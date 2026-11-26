package pvp;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
/** Per-tick collision probe budget; exhaustion is treated as unknown/unsafe. */
public final class ProbeBudget implements BiPredicate<Double,Double> {
private final BiPredicate<Double,Double> probe;
private final LongSupplier clock;
private final long start,nanos;
private final int limit;
private int calls;
public ProbeBudget(BiPredicate<Double,Double> probe,int limit,long nanos) { this(probe,limit,nanos,System::nanoTime); }
public ProbeBudget(BiPredicate<Double,Double> probe,int limit,long nanos,LongSupplier clock) {
if(limit<=0 || nanos<=0) throw new IllegalArgumentException("Invalid budget");
this.probe=probe; this.clock=clock; this.limit=limit; this.nanos=nanos; start=clock.getAsLong();
}
public boolean exhausted() { return calls>=limit || elapsed()>=nanos; }
public long elapsed() { return Math.max(0,clock.getAsLong()-start); }
public int calls() { return calls; }
@Override public boolean test(Double x,Double z) {
if(exhausted() || !Double.isFinite(x) || !Double.isFinite(z)) return false;
calls++;
return probe.test(x,z);
}
}
