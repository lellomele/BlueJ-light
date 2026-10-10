/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public class TestLightTranslations
{
    private Properties labels(String language) throws Exception
    {
        Properties labels = new Properties();
        try (var input = Files.newInputStream(Path.of("lib", language, "labels"))) { labels.load(input); }
        return labels;
    }
    @Test public void allLightAndJdkLabelsHaveAnItalianTranslation() throws Exception
    {
        Properties english = labels("english"), italian = labels("italian");
        int checked = 0;
        for (String key : english.stringPropertyNames())
            if (key.startsWith("light.") || key.startsWith("jdk."))
            { assertNotNull(key, italian.getProperty(key)); assertFalse(key, italian.getProperty(key).isBlank()); checked++; }
        assertTrue(checked > 70);
        assertTrue(italian.getProperty("light.format.stale").contains("\u00e8 cambiato"));
        assertTrue(italian.getProperty("light.exercise.trust").contains("non \u00e8 una sandbox"));
    }
    @Test public void englishSnippetDescriptionsAreProvidedByTheCatalog() throws Exception
    {
        Properties italian = labels("italian"), catalog = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("lib", "snippets.properties"))) { catalog.load(reader); }
        int count = 0;
        for (String key : italian.stringPropertyNames())
            if (key.startsWith("light.snippets.description."))
            { assertNotNull(key, catalog.getProperty(key.substring("light.snippets.description.".length()) + ".description")); count++; }
        assertEquals(17, count);
    }
    @Test public void everyKnownDiagnosticHasTwoLanguageVersions()
    {
        List<String> codes = List.of("compiler.err.invalid.meth.decl.ret.type.req", "compiler.err.cant.resolve",
            "compiler.err.prob.found.req", "compiler.err.cant.apply.symbol", "compiler.err.non-static.cant.be.ref",
            "compiler.err.unreported.exception", "compiler.err.missing.ret.stmt", "compiler.err.var.might.not.have.been.initialized",
            "compiler.err.expected", "compiler.err.already.defined", "unknown");
        for (String code : codes)
        {
            var english = ErrorExplanations.explain(code, Locale.ENGLISH);
            var italian = ErrorExplanations.explain(code, Locale.ITALIAN);
            assertFalse(english.concept().isBlank()); assertFalse(italian.concept().isBlank());
            assertNotEquals(english.concept(), italian.concept());
            assertEquals(english.hints().size(), italian.hints().size());
            for (int index = 0; index < english.hints().size(); index++)
            { assertFalse(italian.hints().get(index).isBlank()); assertNotEquals(english.hints().get(index), italian.hints().get(index)); }
        }
    }
    @Test public void incompatibilityAndNegationsAreNotReversed()
    {
        var english = ErrorExplanations.explain("compiler.err.prob.found.req", Locale.ENGLISH);
        var italian = ErrorExplanations.explain("compiler.err.prob.found.req", Locale.ITALIAN);
        assertEquals("Incompatible types", english.concept()); assertEquals("Tipi incompatibili", italian.concept());
        assertTrue(italian.hints().getFirst().contains("non corrisponde"));
        assertTrue(italian.hints().get(1).contains("non \u00e8 sempre"));
        assertTrue(ErrorExplanations.explain("compiler.err.non-static.cant.be.ref", Locale.ITALIAN).hints().getFirst().contains("non esiste"));
        assertTrue(ErrorExplanations.explain("compiler.err.invalid.meth.decl.ret.type.req", Locale.ITALIAN).hints().get(1).contains("non aggiungere void"));
    }
}
