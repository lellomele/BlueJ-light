/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestExercisePreparation
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @Test public void cancellingBeforeSourcePreparationFinishesCannotStartATestProcess() throws Exception
    {
        Path root=folder.getRoot().toPath(),source=root.resolve("Example.java");Files.writeString(source,"class Example {}\n");
        ExerciseRunner runner=new ExerciseRunner();runner.cancel();assertTrue(runner.isCancelled());
        try{runner.run(root,List.of("ExampleTest"),"");fail("Cancelled run started");}catch(IOException expected){assertEquals("Cancelled",expected.getMessage());}
        assertEquals("class Example {}\n",Files.readString(source));
    }
}
