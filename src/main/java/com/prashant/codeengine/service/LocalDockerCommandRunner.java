package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LocalDockerCommandRunner implements DockerCommandRunner {

    @Override
    public CodeExecutionResultDTO run(
            List<String> command,
            String input,
            long timeoutSeconds,
            int stdoutLimitBytes,
            int stderrLimitBytes) {
        Process process = null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        AtomicBoolean stdoutExceeded = new AtomicBoolean();
        AtomicBoolean stderrExceeded = new AtomicBoolean();
        Thread outputThread = null;
        Thread errorThread = null;

        try {
            process = new ProcessBuilder(command).start();
            Process runningProcess = process;
            outputThread = streamThread(runningProcess.getInputStream(), output,
                    stdoutLimitBytes, stdoutExceeded, runningProcess);
            errorThread = streamThread(runningProcess.getErrorStream(), error,
                    stderrLimitBytes, stderrExceeded, runningProcess);
            outputThread.start();
            errorThread.start();

            try (OutputStream stdin = process.getOutputStream()) {
                if (input != null && !input.isEmpty()) {
                    stdin.write(input.getBytes(Charset.defaultCharset()));
                    stdin.write(System.lineSeparator().getBytes(Charset.defaultCharset()));
                }
            }

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            boolean timedOut = false;
            while (process.isAlive()) {
                if (stdoutExceeded.get() || stderrExceeded.get()) {
                    process.destroyForcibly();
                    break;
                }
                if (System.nanoTime() >= deadline) {
                    timedOut = true;
                    process.destroyForcibly();
                    break;
                }
                Thread.sleep(10);
            }

            process.waitFor(1, TimeUnit.SECONDS);
            joinReader(outputThread);
            joinReader(errorThread);

            if (stdoutExceeded.get()) {
                return result("OUTPUT_LIMIT_EXCEEDED", "[OUTPUT LIMIT EXCEEDED]",
                        "Program output exceeded the maximum allowed size of "
                                + stdoutLimitBytes + " bytes.", -1);
            }
            if (stderrExceeded.get()) {
                return result("OUTPUT_LIMIT_EXCEEDED", output.toString(Charset.defaultCharset()),
                        "Program error output exceeded the maximum allowed size of "
                                + stderrLimitBytes + " bytes.", -1);
            }
            if (timedOut) {
                return result("TIME_LIMIT_EXCEEDED", output.toString(Charset.defaultCharset()),
                        "Code execution timed out after " + timeoutSeconds + " seconds.", -1);
            }

            int exitCode = process.exitValue();
            String errorText = error.toString(Charset.defaultCharset());
            return result(exitCode == 0 ? "SUCCESS" : "RUNTIME_ERROR",
                    output.toString(Charset.defaultCharset()),
                    errorText.isEmpty() ? null : errorText, exitCode);
        } catch (Exception exception) {
            throw new IllegalStateException("Docker command execution failed.", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            close(process);
            joinReader(outputThread);
            joinReader(errorThread);
        }
    }

    private static Thread streamThread(
            InputStream stream,
            ByteArrayOutputStream target,
            int maxBytes,
            AtomicBoolean exceeded,
            Process process) {
        return new Thread(() -> {
            byte[] buffer = new byte[4096];
            int count;
            int total = 0;
            try (stream) {
                while ((count = stream.read(buffer)) != -1) {
                    int remaining = maxBytes - total;
                    int accepted = Math.min(count, Math.max(remaining, 0));
                    if (accepted > 0) {
                        target.write(buffer, 0, accepted);
                        total += accepted;
                    }
                    // Sirf tab exceeded maano jab limit ke baad extra byte aaye.
                    // Exactly maxBytes ka output valid hai.
                    if (count > remaining) {
                        exceeded.set(true);
                        process.destroyForcibly();
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        });
    }

    private static void close(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }
    }

    private static void joinReader(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(1000);
            if (thread.isAlive()) {
                thread.interrupt();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            thread.interrupt();
        }
    }

    private static CodeExecutionResultDTO result(
            String status, String output, String error, int exitCode) {
        CodeExecutionResultDTO result = new CodeExecutionResultDTO();
        result.setStatus(status);
        result.setOutput(output);
        result.setError(error);
        result.setExitCode(exitCode);
        return result;
    }
}