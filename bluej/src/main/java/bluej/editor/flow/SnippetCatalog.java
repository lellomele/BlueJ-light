/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import bluej.Config;
import bluej.utility.Debug;

public final class SnippetCatalog
{
    private long stamp = Long.MIN_VALUE;
    private Properties definitions = new Properties();

    public List<SnippetCompletion> completions(boolean inMethod)
    {
        File builtins = new File(Config.getBlueJLibDir(), "snippets.properties");
        File custom = Config.getUserConfigFile("snippets.properties");
        long currentStamp = builtins.lastModified() * 31 + custom.lastModified();
        if (stamp != currentStamp)
        {
            definitions = new Properties();
            customDescriptions.clear();
            load(builtins, false);
            load(custom, true);
            stamp = currentStamp;
        }
        List<SnippetCompletion> result = new ArrayList<>();
        definitions.stringPropertyNames().stream().filter(key -> key.endsWith(".body")).sorted().forEach(key -> {
            String name = key.substring(0, key.length() - 5);
            String body = definitions.getProperty(key);
            String context = definitions.getProperty(name + ".context", "method");
            if (!body.isBlank() && (context.equals("any") || context.equals(inMethod ? "method" : "class")))
            {
                try
                {
                    new SnippetTemplate(body).expand("", "    ");
                    String description = definitions.getProperty(name + ".description", name);
                    if (!customDescriptions.contains(name + ".description"))
                        description = Config.getString("light.snippets.description." + name, description);
                    result.add(new SnippetCompletion(name, description, body, context));
                }
                catch (IllegalArgumentException ex) { Debug.reportError("Invalid snippet: " + name, ex); }
            }
        });
        return result;
    }

    private void load(File file, boolean custom)
    {
        if (!file.isFile()) return;
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Properties loaded = new Properties();
            loaded.load(reader);
            definitions.putAll(loaded);
            if (custom) customDescriptions.addAll(loaded.stringPropertyNames());
        }
        catch (IOException | IllegalArgumentException ex) { Debug.reportError("Could not read snippets: " + file, ex); }
    }

    private final java.util.Set<String> customDescriptions = new java.util.HashSet<>();

    public List<SnippetCompletion> allCompletions()
    {
        java.util.Map<String, SnippetCompletion> all = new java.util.TreeMap<>();
        completions(true).forEach(snippet -> all.put(snippet.getName(), snippet));
        completions(false).forEach(snippet -> all.put(snippet.getName(), snippet));
        return List.copyOf(all.values());
    }
}
