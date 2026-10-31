package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Output-limit boundary tests for LocalDockerCommandRunner.
 *
 * Har test ek chhota Java program chalata hai jo exactly N bytes print karta
 * hai. Isse Windows/Linux dono par same result aata hai (cmd.exe ya echo ki
 * zarurat nahi, newline ka bhi jhanjhat nahi).
 */
class LocalDockerCommandRunnerTests {

    private static final int LIMIT = 1000;
    private static final long TIMEOUT_SECONDS = 20;

    @TempDir
    Path tempDir;

    private List<String> emitCommand(String stream, int bytes)
            throws IOException {

        Path source = tempDir.resolve("Emitter.java");

        Files.writeString(
                source,
                "public class Emitter {\n"
                        + "  public static void main(String[] a) throws Exception {\n"
                        + "    byte[] b = new byte[Integer.parseInt(a[1])];\n"
                        + "    java.util.Arrays.fill(b, (byte) 'a');\n"
                        + "    java.io.PrintStream s ="
                        + " a[0].equals(\"err\") ? System.err : System.out;\n"
                        + "    s.write(b, 0, b.length);\n"
                        + "    s.flush();\n"
                        + "  }\n"
                        + "}\n"
        );

        String javaBinary = Path.of(
                System.getProperty("java.home"),
                "bin",
                "java"
        ).toString();

        return List.of(
                javaBinary,
                source.toString(),
                stream,
                String.valueOf(bytes)
        );
    }

    private CodeExecutionResultDTO run(String stream, int bytes)
            throws IOException {

        return new LocalDockerCommandRunner().run(
                emitCommand(stream, bytes),
                null,
                TIMEOUT_SECONDS,
                LIMIT,
                LIMIT
        );
    }

    // A. stdout limit se chhota
    @Test
    void stdoutBelowLimitSucceeds() throws IOException {
        CodeExecutionResultDTO result = run("out", LIMIT - 1);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(LIMIT - 1, result.getOutput().length());
    }

    // B. stdout exactly limit ke barabar
    @Test
    void stdoutExactlyAtLimitSucceeds() throws IOException {
        CodeExecutionResultDTO result = run("out", LIMIT);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(LIMIT, result.getOutput().length());
    }

    // C. stdout limit se ek byte zyada
    @Test
    void stdoutOneByteOverLimitIsOutputLimitExceeded() throws IOException {
        CodeExecutionResultDTO result = run("out", LIMIT + 1);

        assertEquals("OUTPUT_LIMIT_EXCEEDED", result.getStatus());
    }

    // D. stderr exactly limit ke barabar
    @Test
    void stderrExactlyAtLimitSucceeds() throws IOException {
        CodeExecutionResultDTO result = run("err", LIMIT);

        assertEquals("SUCCESS", result.getStatus());
    }

    // E. stderr limit se ek byte zyada
    @Test
    void stderrOneByteOverLimitIsOutputLimitExceeded() throws IOException {
        CodeExecutionResultDTO result = run("err", LIMIT + 1);

        assertEquals("OUTPUT_LIMIT_EXCEEDED", result.getStatus());
    }
}