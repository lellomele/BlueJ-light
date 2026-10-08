/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class GenerateLongJava {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        for (int requested : new int[]{2000, 10000, 30000}) {
            String name = "LongJava" + requested;
            Path folder = output.resolve(name);
            Files.createDirectories(folder);
            StringBuilder code = new StringBuilder("import java.util.ArrayList;\nimport java.util.List;\n\npublic class " + name + " {\n"
                + "    private final List<String> results = new ArrayList<>();\n    private long checksum;\n\n");
            int index = 0;
            while (code.toString().lines().count() < requested) {
                code.append("    /** Calculates a deterministic sample with nested control flow. */\n")
                    .append("    public int sample").append(index).append("(int seed) {\n")
                    .append("        int total = seed + ").append(index).append(";\n")
                    .append("        String label = \"sample-").append(index).append("\";\n")
                    .append("        for (int item = 0; item < 8; item++) {\n")
                    .append("            if ((item & 1) == 0) {\n                total += item * 3;\n            } else {\n                total -= item;\n            }\n")
                    .append("            switch (item % 3) {\n                case 0: total ^= seed; break;\n                case 1: total += seed; break;\n                default: total = Math.abs(total); break;\n            }\n")
                    .append("        }\n        try {\n            results.add(label + \":\" + total);\n            checksum += total;\n")
                    .append("        } catch (RuntimeException failure) {\n            throw new IllegalStateException(label, failure);\n        }\n")
                    .append("        // A deliberately wide line helps exercise horizontal extent measurement.\n")
                    .append("        boolean valid = label != null && total != Integer.MIN_VALUE && results.size() >= 0 && checksum != Long.MIN_VALUE && label.startsWith(\"sample-\");\n")
                    .append("        return valid ? total : seed;\n    }\n\n");
                index++;
            }
            code.append("}\n");
            Files.writeString(folder.resolve(name + ".java"), code, StandardCharsets.UTF_8);
            Files.writeString(folder.resolve("package.bluej"), "package.numTargets=1\nproject.charset=UTF-8\ntarget1.name=" + name
                + "\ntarget1.type=ClassTarget\ntarget1.x=50\ntarget1.y=80\ntarget1.width=150\ntarget1.height=60\n");
            System.out.println(name + " lines=" + code.toString().lines().count() + " bytes=" + code.length());
        }
    }
}
