package dev.unified.core;

/** Pure formatting without external server data. */
final class Metrics {
private Metrics(){}
static String format(double value){
if(!Double.isFinite(value)||value<0)return "n/a";
return String.format(java.util.Locale.ROOT,"%.2f",value);
}
}
