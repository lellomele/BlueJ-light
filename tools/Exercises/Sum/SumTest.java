/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SumTest
{
    @Test void empty() { assertEquals(0, Sum.firstNumbers(0)); }
    @Test void one() { assertEquals(1, Sum.firstNumbers(1)); }
    @Test void four() { assertEquals(10, Sum.firstNumbers(4)); }
    @Test void negative() { assertEquals(0, Sum.firstNumbers(-3)); }
}
