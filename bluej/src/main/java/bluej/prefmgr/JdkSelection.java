/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GPLv2 with Classpath Exception; see LICENSE.txt. */
package bluej.prefmgr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public record JdkSelection(String mode, Path home)
{
    public static final String FILE_NAME = "jdk-selection.txt";
    public static JdkSelection automatic() { return new JdkSelection("automatic", null); }
    public static JdkSelection bundled() { return new JdkSelection("bundled", null); }
    public static JdkSelection external(Path home) { return new JdkSelection("external", home); }

    public static JdkSelection read(Path preferences) throws IOException
    {
        Path file = preferences.resolve(FILE_NAME);
        if (!Files.exists(file)) return automatic();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new IOException("Empty JDK selection");
        return switch (lines.getFirst()) {
            case "automatic" -> automatic();
            case "bundled" -> bundled();
            case "external" -> {
                if (lines.size() != 2 || lines.get(1).isBlank()) throw new IOException("Invalid external JDK selection");
                yield external(Path.of(lines.get(1)));
            }
            default -> throw new IOException("Unknown JDK selection mode");
        };
    }

    public void save(Path preferences) throws IOException
    {
        if (!List.of("automatic", "bundled", "external").contains(mode)) throw new IOException("Unknown JDK selection mode");
        if (mode.equals("external") && (home == null || JdkInfo.inspect(home).isEmpty()))
            throw new IOException("A JDK 21 Windows x64 is required");
        String text = mode + "\n";
        if (mode.equals("external"))
        {
            String path = home.toAbsolutePath().normalize().toString();
            if (path.contains("\n") || path.contains("\r")) throw new IOException("Invalid JDK path");
            text += path + "\n";
        }
        Files.createDirectories(preferences);
        Path temporary = Files.createTempFile(preferences, "jdk-selection-", ".tmp");
        try
        {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try { Files.move(temporary, preferences.resolve(FILE_NAME), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, preferences.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING); }
        }
        finally { Files.deleteIfExists(temporary); }
    }
}
