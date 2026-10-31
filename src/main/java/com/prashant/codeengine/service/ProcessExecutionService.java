package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import com.prashant.codeengine.exception.CodeExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(
        name = "codeengine.execution.backend",
        havingValue = "local",
        matchIfMissing = true
)
public class ProcessExecutionService implements ExecutionBackend {

    private static final long EXECUTION_TIMEOUT_SECONDS = 5;
    private static final int MAX_OUTPUT_SIZE_BYTES = 1024 * 1024;

    @Override
    public CodeExecutionResultDTO execute(ProcessExecutionRequest request) {
        return execute(
                request.sourceFileName(),
                request.sourceCode(),
                request.compileCommand(),
                request.runCommand(),
                request.input()
        );
    }

    public CodeExecutionResultDTO execute(
            String sourceFileName,
            String sourceCode,
            List<String> compileCommand,
            List<String> runCommand,
            String input) {

        Path tempDir = null;
        Process compileProcess = null;
        Process runProcess = null;
        Thread outputThread = null;
        Thread errorThread = null;

        try {
            CodeExecutionResultDTO result = new CodeExecutionResultDTO();
            tempDir = Files.createTempDirectory("code-execution-");
            Path sourceFile = tempDir.resolve(sourceFileName);
            Files.writeString(sourceFile, sourceCode);

            if (compileCommand != null) {
                ProcessBuilder compileBuilder = new ProcessBuilder(compileCommand);
                compileBuilder.directory(tempDir.toFile());
                compileProcess = compileBuilder.start();

                String compilationError = readStream(compileProcess.getErrorStream());
                int compileExitCode = compileProcess.waitFor();

                if (compileExitCode != 0) {
                    result.setStatus("COMPILE_ERROR");
                    result.setOutput("");
                    result.setError(compilationError);
                    result.setExitCode(compileExitCode);
                    return result;
                }
            }

            ProcessBuilder runBuilder = new ProcessBuilder(runCommand);
            runBuilder.directory(tempDir.toFile());
            runProcess = runBuilder.start();

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(runProcess.getOutputStream()))) {
                if (input != null && !input.isEmpty()) {
                    writer.write(input);
                    writer.newLine();
                }
                writer.flush();
            }

            StringBuilder outputBuilder = new StringBuilder();
            StringBuilder errorBuilder = new StringBuilder();
            AtomicBoolean outputLimitExceeded = new AtomicBoolean(false);
            AtomicBoolean errorLimitExceeded = new AtomicBoolean(false);

            Process outputProcess = runProcess;
            outputThread = new Thread(() -> {
                try {
                    readLimitedStream(
                            outputProcess.getInputStream(),
                            outputBuilder,
                            outputLimitExceeded,
                            outputProcess
                    );
                } catch (IOException ignored) {
                }
            });

            Process errorProcess = runProcess;
            errorThread = new Thread(() -> {
                try {
                    readLimitedStream(
                            errorProcess.getErrorStream(),
                            errorBuilder,
                            errorLimitExceeded,
                            errorProcess
                    );
                } catch (IOException ignored) {
                }
            });

            outputThread.start();
            errorThread.start();

            long startTime = System.nanoTime();
            boolean timeoutExceeded = false;

            while (runProcess.isAlive()) {
                if (outputLimitExceeded.get() || errorLimitExceeded.get()) {
                    runProcess.destroyForcibly();
                    break;
                }

                long elapsedNanos = System.nanoTime() - startTime;
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

                if (elapsedMillis >= TimeUnit.SECONDS.toMillis(EXECUTION_TIMEOUT_SECONDS)) {
                    timeoutExceeded = true;
                    runProcess.destroyForcibly();
                    break;
                }

                Thread.sleep(10);
            }

            if (outputLimitExceeded.get()) {
                safelyTerminateProcess(runProcess);
                closeProcessStreams(runProcess);
                waitForReaderThreads(outputThread, errorThread);

                result.setStatus("OUTPUT_LIMIT_EXCEEDED");
                result.setOutput("[OUTPUT LIMIT EXCEEDED]");
                result.setError("Program output exceeded the maximum allowed size of "
                        + MAX_OUTPUT_SIZE_BYTES + " bytes.");
                result.setExitCode(-1);
                return result;
            }

            if (errorLimitExceeded.get()) {
                safelyTerminateProcess(runProcess);
                closeProcessStreams(runProcess);
                waitForReaderThreads(outputThread, errorThread);

                result.setStatus("OUTPUT_LIMIT_EXCEEDED");
                result.setOutput(outputBuilder.toString());
                result.setError("Program error output exceeded the maximum allowed size of "
                        + MAX_OUTPUT_SIZE_BYTES + " bytes.");
                result.setExitCode(-1);
                return result;
            }

            if (timeoutExceeded) {
                safelyTerminateProcess(runProcess);
                closeProcessStreams(runProcess);
                waitForReaderThreads(outputThread, errorThread);

                result.setStatus("TIME_LIMIT_EXCEEDED");
                result.setOutput(outputBuilder.toString());
                result.setError("Code execution timed out after "
                        + EXECUTION_TIMEOUT_SECONDS + " seconds.");
                result.setExitCode(-1);
                return result;
            }

            runProcess.waitFor();
            waitForReaderThreads(outputThread, errorThread);

            String output = outputBuilder.toString();
            String runtimeError = errorBuilder.toString();
            int runExitCode = runProcess.exitValue();

            if (runExitCode != 0) {
                result.setStatus("RUNTIME_ERROR");
                result.setOutput(output);
                result.setError(runtimeError);
                result.setExitCode(runExitCode);
                return result;
            }

            result.setStatus("SUCCESS");
            result.setOutput(output);
            result.setError(runtimeError.isEmpty() ? null : runtimeError);
            result.setExitCode(runExitCode);
            return result;

        } catch (CodeExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CodeExecutionException("Code execution failed.", e);
        } finally {
            safelyTerminateProcess(runProcess);
            safelyTerminateProcess(compileProcess);
            closeProcessStreams(runProcess);
            closeProcessStreams(compileProcess);
            waitForReaderThreads(outputThread, errorThread);
            cleanup(tempDir);
        }
    }

    private static void readLimitedStream(
            InputStream inputStream,
            StringBuilder builder,
            AtomicBoolean limitExceeded,
            Process process) throws IOException {

        byte[] buffer = new byte[4096];
        int totalBytes = 0;

        try {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                int remainingBytes = MAX_OUTPUT_SIZE_BYTES - totalBytes;

                if (bytesRead > remainingBytes) {
                    if (remainingBytes > 0) {
                        builder.append(new String(buffer, 0, remainingBytes));
                    }
                    totalBytes = MAX_OUTPUT_SIZE_BYTES;
                    limitExceeded.set(true);
                    process.destroyForcibly();
                    break;
                }

                builder.append(new String(buffer, 0, bytesRead));
                totalBytes += bytesRead;

                if (totalBytes >= MAX_OUTPUT_SIZE_BYTES) {
                    limitExceeded.set(true);
                    process.destroyForcibly();
                    break;
                }
            }
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String readStream(InputStream inputStream) throws IOException {
        StringBuilder result = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append(System.lineSeparator());
            }
        }

        return result.toString();
    }

    private static void safelyTerminateProcess(Process process) {
        if (process == null) {
            return;
        }

        try {
            if (process.isAlive()) {
                process.destroy();
                boolean terminated = process.waitFor(200, TimeUnit.MILLISECONDS);

                if (!terminated && process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(500, TimeUnit.MILLISECONDS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void closeProcessStreams(Process process) {
        if (process == null) {
            return;
        }

        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
        }

        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {
        }

        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }
    }

    private static void waitForReaderThreads(Thread outputThread, Thread errorThread) {
        waitForThread(outputThread);
        waitForThread(errorThread);
    }

    private static void waitForThread(Thread thread) {
        if (thread == null) {
            return;
        }

        try {
            thread.join(1000);
            if (thread.isAlive()) {
                thread.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            thread.interrupt();
        }
    }

    private static void cleanup(Path tempDir) {
        if (tempDir == null) {
            return;
        }

        try {
            Files.walk(tempDir)
                    .sorted((path1, path2) -> path2.compareTo(path1))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
