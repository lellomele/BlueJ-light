/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestPackageSourceMove
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private Path root,source,destination,preferences;
    private final String savedText="package child; public class Example {}\n";
    @Before public void setup() throws Exception
    {
        root=folder.newFolder("project").toPath();Files.writeString(root.resolve("package.bluej"),"");Path child=Files.createDirectory(root.resolve("child"));Files.writeString(child.resolve("package.bluej"),"");
        source=root.resolve("Example.java");destination=child.resolve("Example.java");Files.writeString(source,savedText);preferences=folder.newFolder("preferences").toPath();
    }
    private SourceSaveService.Snapshot saved()
    {return new SourceSaveService.Snapshot(new SourceSaveService.Owner(),1,source,savedText,StandardCharsets.UTF_8,savedText,true,false);}
    private void fails(CompletableFuture<?> request) throws Exception
    {try{request.get(5,TimeUnit.SECONDS);fail("Expected a safe refusal");}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof IOException);}}

    @Test public void movesWithoutLosingANewerUnsavedDraftOrReusingTheVacatedHistoryIdentity() throws Exception
    {
        LocalHistory history=new LocalHistory(preferences);String newest=savedText+"// newest unsaved\n";history.snapshot(source,savedText,"saved");history.draft(source,newest,LocalHistory.hash(savedText),"editor",2);
        var snapshot=saved();
        try(var service=new SourceSaveService())
        {
            var moved=service.movePackage(snapshot,destination,root,history).get(5,TimeUnit.SECONDS);assertNull(moved.historyWarning());assertTrue(snapshot.owner().isRetired());
        }
        assertFalse(Files.exists(source));assertEquals(savedText,Files.readString(destination));LocalHistory restarted=new LocalHistory(preferences);
        assertEquals(destination,restarted.effectiveSource(source));assertEquals(newest,restarted.recover(destination,savedText).orElseThrow().text());
        Files.writeString(source,"public class Example {}\n");LocalHistory replacement=new LocalHistory(preferences);assertEquals(source,replacement.effectiveSource(source));assertTrue(replacement.recover(source,"public class Example {}\n").isEmpty());
    }
    @Test public void anExistingDestinationIsPreservedAndTheOriginalOwnerCanRetry() throws Exception
    {
        Files.writeString(destination,"Existing user source\n");var snapshot=saved();
        try(var service=new SourceSaveService())
        {
            fails(service.movePackage(snapshot,destination,root,new LocalHistory(preferences)));assertFalse(snapshot.owner().isRetired());
            assertEquals(savedText,Files.readString(source));assertEquals("Existing user source\n",Files.readString(destination));
            var retry=new SourceSaveService.Snapshot(snapshot.owner(),2,source,"retry\n",StandardCharsets.UTF_8,savedText,true,false);assertTrue(service.save(retry).get(5,TimeUnit.SECONDS).saved());
        }
    }
    @Test public void neitherSourceNorDestinationCanEscapeTheSuppliedProject() throws Exception
    {
        Path outside=folder.newFolder("outside").toPath().resolve("Example.java");
        try(var service=new SourceSaveService())
        {fails(service.movePackage(saved(),outside,root,new LocalHistory(preferences)));assertEquals(savedText,Files.readString(source));assertFalse(Files.exists(outside));}
    }
    @Test public void interruptionAfterTheMoveBeforeAcknowledgementResolvesTheNewPackage() throws Exception
    {
        LocalHistory history=new LocalHistory(preferences);history.draft(source,savedText+"// unsaved\n",LocalHistory.hash(savedText),"editor",2);
        history.preparePathChange(source,destination,root);Files.move(source,destination);
        LocalHistory restarted=new LocalHistory(preferences);assertEquals(destination,restarted.effectiveSource(source));assertEquals(savedText+"// unsaved\n",restarted.recover(destination,savedText).orElseThrow().text());
    }
    @Test public void anUnrelatedRootCannotAuthorizeHistoryRedirects() throws Exception
    {
        Path unrelated=folder.newFolder("unrelated").toPath();Files.writeString(unrelated.resolve("package.bluej"),"");LocalHistory history=new LocalHistory(preferences);
        try{history.preparePathChange(source,destination,unrelated);fail("Unrelated root was accepted");}catch(IOException expected){}
        assertEquals(source,new LocalHistory(preferences).effectiveSource(source));assertTrue(Files.exists(source));
    }
    @Test public void crossPackageHardLinksAreNotMistakenForACaseOnlyRename() throws Exception
    {
        try{Files.createLink(destination,source);}catch(UnsupportedOperationException|IOException unavailable){Assume.assumeNoException(unavailable);}
        assertTrue(Files.isSameFile(source,destination));try(var service=new SourceSaveService()){fails(service.movePackage(saved(),destination,root,new LocalHistory(preferences)));}
        assertTrue(Files.exists(source));assertTrue(Files.exists(destination));assertEquals(savedText,Files.readString(source));
    }
}
