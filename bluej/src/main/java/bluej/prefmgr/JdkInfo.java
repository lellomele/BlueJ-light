/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GPLv2 with Classpath Exception; see LICENSE.txt. */
package bluej.prefmgr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public record JdkInfo(Path home, String version, String vendor)
{
    public static Optional<JdkInfo> inspect(Path directory)
    {
        try
        {
            Path home = directory.toRealPath();
            Properties release = new Properties();
            try (InputStream stream = Files.newInputStream(home.resolve("release"))) { release.load(stream); }
            String version = unquote(release.getProperty("JAVA_VERSION", ""));
            String arch = unquote(release.getProperty("OS_ARCH", ""));
            if (!version.matches("21(?:[.+-].*)?") || !List.of("amd64", "x86_64", "x64").contains(arch))
                return Optional.empty();
            for (String executable : List.of("java.exe", "javaw.exe", "javac.exe"))
                if (!isAmd64Executable(home.resolve("bin").resolve(executable))) return Optional.empty();
            return Optional.of(new JdkInfo(home, version, unquote(release.getProperty("IMPLEMENTOR", "JDK"))));
        }
        catch (IOException | RuntimeException ex) { return Optional.empty(); }
    }

    private static String unquote(String value)
    {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
            ? value.substring(1, value.length() - 1) : value;
    }

    private static boolean isAmd64Executable(Path executable) throws IOException
    {
        try (FileChannel channel = FileChannel.open(executable, StandardOpenOption.READ))
        {
            ByteBuffer dos = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
            if (channel.read(dos) != 64 || dos.getShort(0) != 0x5a4d) return false;
            int offset = dos.getInt(60);
            if (offset < 64 || offset > channel.size() - 26) return false;
            ByteBuffer pe = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
            channel.position(offset);
            return channel.read(pe) == 26 && pe.getInt(0) == 0x00004550
                && Short.toUnsignedInt(pe.getShort(4)) == 0x8664 && pe.getShort(24) == 0x20b;
        }
    }

    public static List<JdkInfo> discover(Path applicationRoot, Path selected)
    {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        candidates.add(Path.of(System.getProperty("java.home")));
        candidates.add(applicationRoot.resolve("runtime"));
        if (selected != null) candidates.add(selected);
        for (String variable : List.of("BLUEJ_LIGHT_JDK", "JAVA_HOME", "JDK_HOME"))
        {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank())
                try { candidates.add(Path.of(value)); } catch (RuntimeException ignored) { }
        }
        String programs = System.getenv("ProgramFiles");
        if (programs != null)
            for (String vendor : List.of("Java", "Eclipse Adoptium", "Microsoft", "BellSoft", "Zulu"))
                addChildren(Path.of(programs, vendor), candidates);
        Path user = Path.of(System.getProperty("user.home"));
        addChildren(user.resolve(".jdks"), candidates);
        addChildren(user.resolve("Downloads"), candidates);
        List<JdkInfo> found = new ArrayList<>();
        LinkedHashSet<Path> seen = new LinkedHashSet<>();
        for (Path candidate : candidates)
            for (Path path : List.of(candidate, candidate.resolve("jdk"), candidate.resolve("bluej/jdk")))
                inspect(path).filter(info -> seen.add(info.home())).ifPresent(found::add);
        found.sort(java.util.Comparator.comparing(JdkInfo::version).reversed().thenComparing(info -> info.home().toString()));
        return List.copyOf(found);
    }

    private static void addChildren(Path parent, LinkedHashSet<Path> candidates)
    {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(parent))
        {
            int count = 0;
            for (Path child : children)
            {
                if (++count > 300) break;
                if (Files.isDirectory(child)) candidates.add(child);
            }
        }
        catch (IOException | SecurityException ignored) { }
    }

    @Override public String toString() { return vendor + " " + version + " - " + home; }
}
