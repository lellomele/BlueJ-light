/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.extensions2;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestJavaSourceType
{
    @Test
    public void onlyExposesJavaSource()
    {
        assertArrayEquals(new SourceType[] { SourceType.NONE, SourceType.Java }, SourceType.values());
        assertEquals("java", SourceType.Java.getExtension());
        assertEquals("", SourceType.NONE.getExtension());
        assertEquals(SourceType.Java, SourceType.getEnum("JAVA"));
    }

    @Test
    public void unsupportedProjectMetadataDoesNotCrashLoading()
    {
        for (String value : new String[] { null, "null", "", "unsupported-language" })
            assertEquals(SourceType.NONE, SourceType.getEnum(value));
    }
}
