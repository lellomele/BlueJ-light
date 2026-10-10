/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-09. GPLv2 with Classpath Exception. */
package bluej.light;

import bluej.parser.lexer.JavaLexer;
import bluej.parser.lexer.JavaTokenTypes;
import bluej.parser.lexer.LocatableToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Formats a snapshot, never the project file; rejects any non-whitespace code change. */
@OnThread(Tag.Any)
public final class JavaFormatter
{
    public record Result(String text, int caret) {}
    public static Result format(Path executable, String source, int caret) throws IOException, InterruptedException
    {
        if (source.getBytes(StandardCharsets.UTF_8).length > 5 * 1024 * 1024) throw new IOException("Source exceeds 5 MiB");
        List<LocatableToken> before = tokens(source);
        Map<String, String> protectedBlocks = new LinkedHashMap<>();
        StringBuilder input = new StringBuilder(source);
        // Keep incidental text-block whitespace byte-for-byte, including its closing delimiter.
        String nonce = UUID.randomUUID().toString().replace("-", "");
        for (int i = before.size() - 1; i >= 0; i--)
        {
            LocatableToken token = before.get(i);
            if (token.getType() == JavaTokenTypes.STRING_LITERAL_MULTILINE)
            {
                String placeholder = "\"BLUEJ_FORMAT_" + nonce + "_" + i + "\"";
                protectedBlocks.put(placeholder, source.substring(token.getPosition(), token.getEndPosition()));
                input.replace(token.getPosition(), token.getEndPosition(), placeholder);
            }
        }
        Path work = Files.createTempDirectory("bluej-format-");
        Process process = null;
        try
        {
            Path original = work.resolve("input.java"), output = work.resolve("output.java"), error = work.resolve("error.txt");
            Files.writeString(original, input, StandardCharsets.UTF_8);
            process = new ProcessBuilder(executable.toAbsolutePath().toString(), "--options=none", "--project=none",
                "--mode=java", "--style=allman", "--indent=spaces=4", "--break-one-line-headers",
                "--pad-oper", "--pad-header", "--lineend=linux", "--quiet")
                .directory(work.toFile()).redirectInput(original.toFile()).redirectOutput(output.toFile()).redirectError(error.toFile()).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IOException("Formatter time limit exceeded");
            if (process.exitValue() != 0 || Files.size(output) > 10 * 1024 * 1024)
                throw new IOException("Formatter failed (exit " + process.exitValue() + ")");
            String formatted = Files.readString(output, StandardCharsets.UTF_8).replace("\r\n", "\n");
            for (var block : protectedBlocks.entrySet())
            {
                int first = formatted.indexOf(block.getKey());
                if (first < 0 || formatted.indexOf(block.getKey(), first + 1) >= 0) throw new IOException("Text-block protection failed");
                formatted = formatted.substring(0, first) + block.getValue() + formatted.substring(first + block.getKey().length());
            }
            // Keep the editor's original final-newline convention.
            if (!source.endsWith("\n") && formatted.endsWith("\n")) formatted = formatted.substring(0, formatted.length() - 1);
            if (source.endsWith("\n") && !formatted.endsWith("\n")) formatted += "\n";
            return verified(source, formatted, caret);
        }
        finally
        {
            if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(2, TimeUnit.SECONDS); }
            try (var files = Files.walk(work)) { for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }

    public static Result verified(String source, String formatted, int caret) throws IOException
    {
        List<LocatableToken> before = tokens(source), after = tokens(formatted);
        if (before.size() != after.size()) throw new IOException("Formatting changed Java tokens");
        int mapped = Math.min(caret, formatted.length()); boolean positioned = false;
        for (int i = 0; i < before.size(); i++)
        {
            LocatableToken a = before.get(i), b = after.get(i);
            if (a.getType() != b.getType() || !Objects.equals(text(a), text(b))) throw new IOException("Formatting changed Java tokens");
            if (!positioned && caret < a.getEndPosition())
            {
                mapped = b.getPosition() + Math.max(0, Math.min(caret - a.getPosition(), b.getLength())); positioned = true;
            }
        }
        if (caret >= source.length()) mapped = formatted.length();
        return new Result(formatted, mapped);
    }
    private static String text(LocatableToken token)
    {
        String value = token.getText();
        if (token.getType() == JavaTokenTypes.ML_COMMENT)
            return value.lines().map(String::stripLeading).collect(java.util.stream.Collectors.joining("\n"));
        return value;
    }
    // The lexer has no UI calls: a fresh lexer/reader is confined to this worker.
    // Its only shared data is the keyword table, populated during class initialization.
    @SuppressWarnings("threadchecker")
    private static List<LocatableToken> tokens(String text) throws IOException
    {
        JavaLexer lexer = new JavaLexer(new StringReader(text), true, true);
        List<LocatableToken> result = new ArrayList<>(); LocatableToken token;
        while ((token = lexer.nextToken()).getType() != JavaTokenTypes.EOF)
        {
            if (token.getPosition() < 0 || token.getEndPosition() > text.length()) throw new IOException("Invalid Java token range");
            result.add(token);
        }
        return result;
    }
}
