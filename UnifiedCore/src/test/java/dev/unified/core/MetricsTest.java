package dev.unified.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class MetricsTest {
@Test void finiteValuesAreFormatted(){assertEquals("19.99",Metrics.format(19.987));}
@Test void abnormalValuesAreUnavailable(){
assertEquals("n/a",Metrics.format(Double.NaN));
assertEquals("n/a",Metrics.format(Double.POSITIVE_INFINITY));
assertEquals("n/a",Metrics.format(-2));
}
}
