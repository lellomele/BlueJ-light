/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GPLv2 with Classpath Exception. */
package bluej.prefmgr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestJdkSelection
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    public static Path fakeJdk(Path home, String version, String architecture) throws Exception
    {
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\nOS_ARCH=\"" + architecture + "\"\nIMPLEMENTOR=\"Test vendor\"\n");
        ByteBuffer pe = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        pe.putShort(0, (short)0x5a4d);
        pe.putInt(60, 128);
        pe.putInt(128, 0x00004550);
        pe.putShort(132, (short)0x8664);
        pe.putShort(152, (short)0x20b);
        for (String name : List.of("java.exe", "javaw.exe", "javac.exe")) Files.write(home.resolve("bin").resolve(name), pe.array());
        return home;
    }

    @Test public void validatesVersionArchitectureAndFullJdk() throws Exception
    {
        Path home = fakeJdk(temporary.newFolder("jdk").toPath(), "21.0.6", "amd64");
        assertEquals("21.0.6", JdkInfo.inspect(home).orElseThrow().version());
        Files.delete(home.resolve("bin/javac.exe"));
        assertTrue(JdkInfo.inspect(home).isEmpty());
        fakeJdk(home, "17.0.1", "amd64");
        assertTrue(JdkInfo.inspect(home).isEmpty());
        fakeJdk(home, "21.0.6", "aarch64");
        assertTrue(JdkInfo.inspect(home).isEmpty());
        fakeJdk(home, "21.0.6", "amd64");
        Files.write(home.resolve("bin/javaw.exe"), new byte[64]);
        assertTrue(JdkInfo.inspect(home).isEmpty());
    }

    @Test public void roundTripsModesAndPathsWithoutChangingEnvironment() throws Exception
    {
        Path preferences = temporary.newFolder("preferences").toPath();
        Path home = fakeJdk(temporary.newFolder("jdk con spazi").toPath(), "21.0.9+10", "x86_64");
        assertEquals(JdkSelection.automatic(), JdkSelection.read(preferences));
        JdkSelection.external(home).save(preferences);
        assertEquals(JdkSelection.external(home), JdkSelection.read(preferences));
        JdkSelection.bundled().save(preferences);
        assertEquals(JdkSelection.bundled(), JdkSelection.read(preferences));
        JdkSelection.automatic().save(preferences);
        assertEquals(JdkSelection.automatic(), JdkSelection.read(preferences));
        assertEquals(1, Files.list(preferences).count());
    }

    @Test public void invalidSelectionDoesNotOverwritePreviousChoice() throws Exception
    {
        Path preferences = temporary.newFolder("prefs").toPath();
        JdkSelection.bundled().save(preferences);
        try { JdkSelection.external(preferences).save(preferences); fail(); }
        catch (java.io.IOException expected) { }
        assertEquals(JdkSelection.bundled(), JdkSelection.read(preferences));
        Files.writeString(preferences.resolve(JdkSelection.FILE_NAME), "external\n");
        try { JdkSelection.read(preferences); fail(); }
        catch (java.io.IOException expected) { }
    }
}
