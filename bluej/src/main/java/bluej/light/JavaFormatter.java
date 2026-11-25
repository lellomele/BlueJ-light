/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-11-25. GPLv2 with Classpath Exception. */
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
                "--pad-oper", "--pad-header", "--indent-col1-comments", "--lineend=linux", "--quiet")
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
            return verified(source, before, formatted, caret);
        }
        finally
        {
            if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(2, TimeUnit.SECONDS); }
            try (var files = Files.walk(work)) { for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }

    public static Result verified(String source, String formatted, int caret) throws IOException
    {
        return verified(source, tokens(source), formatted, caret);
    }

    private static Result verified(String source, List<LocatableToken> before, String formatted, int caret) throws IOException
    {
        List<LocatableToken> after = tokens(formatted);
        StringBuilder checked = new StringBuilder(formatted.length());
        int previousEnd = 0;
        int mapped = -1;
        boolean rebuiltComment = false;
        for (int i = 0; i < before.size(); i++)
        {
            if (i >= after.size()) throw changedToken(before.get(i));
            LocatableToken a = before.get(i), b = after.get(i);
            if (a.getType() != b.getType()) throw changedToken(a);
            checked.append(formatted, previousEnd, b.getPosition());
            int start = checked.length();
            if (a.getLength() == b.getLength() && source.regionMatches(a.getPosition(), formatted, b.getPosition(), a.getLength())
                && Objects.equals(a.getText(), b.getText()))
                checked.append(source, a.getPosition(), a.getEndPosition());
            else if (a.getType() == JavaTokenTypes.ML_COMMENT || a.getType() == JavaTokenTypes.SL_COMMENT) {
                appendComment(checked, a, source.substring(a.getPosition(), a.getEndPosition()),
                    formatted.substring(b.getPosition(), b.getEndPosition()));
                rebuiltComment = true;
            }
            else
                throw changedToken(a);
            if (mapped < 0 && caret < a.getEndPosition())
                mapped = start + Math.clamp(caret - a.getPosition(), 0, checked.length() - start);
            previousEnd = b.getEndPosition();
        }
        if (after.size() > before.size())
            throw new IOException("Formatting introduced a Java token at output line " + after.get(before.size()).getLine());
        checked.append(formatted, previousEnd, formatted.length());
        if (mapped < 0) mapped = caret >= source.length() ? checked.length() : Math.min(caret, checked.length());
        String result = checked.toString();
        if (rebuiltComment) verifyRebuiltTokens(before, result);
        return new Result(result, mapped);
    }

    // Verify what will actually reach the editor, after restoring the comment margins.
    private static void verifyRebuiltTokens(List<LocatableToken> before, String result) throws IOException
    {
        List<LocatableToken> after = tokens(result);
        if (before.size() != after.size()) throw new IOException("Comment restoration changed the Java token count");
        for (int i = 0; i < before.size(); i++) {
            LocatableToken a = before.get(i), b = after.get(i);
            if (a.getType() != b.getType()) throw changedToken(a);
            if (Objects.equals(a.getText(), b.getText())) continue;
            if (a.getType() != JavaTokenTypes.ML_COMMENT
                || !a.getText().lines().map(String::stripLeading).toList().equals(b.getText().lines().map(String::stripLeading).toList()))
                throw changedToken(a);
        }
    }

    private static IOException changedToken(LocatableToken token)
    {
        return new IOException("Formatting changed Java tokens at source line " + token.getLine() + ", column " + token.getColumn());
    }

    // AStyle trims comment lines, even inside Javadoc. Keep the original right
    // margin (including Markdown hard breaks), allowing only block-comment reindentation.
    private static void appendComment(StringBuilder checked, LocatableToken token, String original, String formatted) throws IOException
    {
        String[] oldLines = original.split("\n", -1);
        String[] newLines = formatted.split("\n", -1);
        if (oldLines.length != newLines.length) throw changedToken(token);
        for (int i = 0; i < oldLines.length; i++) {
            String oldLine = oldLines[i], newLine = newLines[i];
            int oldEnd = paddingStart(oldLine), newEnd = paddingStart(newLine);
            int oldStart = 0, newStart = 0;
            if (token.getType() == JavaTokenTypes.ML_COMMENT) {
                while (oldStart < oldEnd && horizontalSpace(oldLine.charAt(oldStart))) oldStart++;
                while (newStart < newEnd && horizontalSpace(newLine.charAt(newStart))) newStart++;
            }
            if (oldEnd - oldStart != newEnd - newStart || !oldLine.regionMatches(oldStart, newLine, newStart, oldEnd - oldStart))
                throw changedToken(token);
            if (i > 0) checked.append('\n');
            checked.append(newLine, 0, newEnd).append(oldLine, oldEnd, oldLine.length());
        }
    }

    private static int paddingStart(String line)
    {
        int end = line.endsWith("\r") ? line.length() - 1 : line.length();
        while (end > 0 && horizontalSpace(line.charAt(end - 1))) end--;
        return end;
    }

    private static boolean horizontalSpace(char ch)
    {
        return ch == ' ' || ch == '\t';
    }
    // The lexer has no UI calls: a fresh lexer/reader is confined to this worker.
    // Its only shared data is the keyword table, populated during class initialization.
    @SuppressWarnings("threadchecker")
    private static List<LocatableToken> tokens(String text) throws IOException
    {
        JavaLexer lexer = new JavaLexer(new StringReader(text), true, true);
        List<LocatableToken> result = new ArrayList<>(); LocatableToken token;
        int end = 0;
        while ((token = lexer.nextToken()).getType() != JavaTokenTypes.EOF)
        {
            if (token.getPosition() < end || token.getEndPosition() <= token.getPosition() || token.getEndPosition() > text.length())
                throw new IOException("Invalid Java token range at line " + token.getLine());
            checkGap(text, end, token.getPosition());
            result.add(token);
            end = token.getEndPosition();
        }
        checkGap(text, end, text.length());
        return result;
    }

    // Only literal Java whitespace may be copied from outside the verified tokens.
    // In particular, decline formatting if an escape makes the lexer's offsets unreliable.
    private static void checkGap(String text, int start, int end) throws IOException
    {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (!horizontalSpace(c) && c != '\r' && c != '\n' && c != '\f')
                throw new IOException("Unverified text between Java tokens at offset " + i);
        }
    }
}
