/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestPrivacy
{
    @Test public void telemetryImplementationIsAbsent()
    {
        for (String name : new String[]{"DataCollector", "DataCollectorImpl", "DataSubmitter",
                "DataCollectionDialog", "DataCollectionCompileObserverWrapper", "CodeAnonymiser"})
        {
            assertNull(name, getClass().getClassLoader().getResource("bluej/collect/" + name + ".class"));
        }
    }

    @Test public void startupContainsNoStatisticsSender() throws IOException
    {
        try (var stream = getClass().getClassLoader().getResourceAsStream("bluej/Main.class"))
        {
            assertNotNull(stream);
            String bytecode = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(bytecode.contains("stats.bluej.org"));
            assertFalse(bytecode.contains("stats.greenfoot.org"));
            assertFalse(bytecode.contains("Updating central stats"));
            assertFalse(bytecode.contains("updateStats"));
        }
    }

    @Test public void editorCountersAreAbsent()
    {
        Set<String> methods = Arrays.stream(Config.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName).collect(Collectors.toSet());
        assertFalse(methods.contains("recordEditorOpen"));
        assertFalse(methods.contains("getEditorCount"));
        assertFalse(methods.contains("resetEditorsCount"));
    }

    @Test public void teamImplementationIsAbsent()
    {
        for (String name : new String[]{"TeamSettingsController", "Repository", "actions/TeamActionGroup", "git/GitRepository"})
            assertNull(name, getClass().getClassLoader().getResource("bluej/groupwork/" + name + ".class"));
    }
}
