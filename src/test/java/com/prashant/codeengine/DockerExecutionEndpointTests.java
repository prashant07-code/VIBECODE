package com.prashant.codeengine;

import com.prashant.codeengine.dto.CodeExecutionRequestDTO;
import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import com.prashant.codeengine.service.LocalDockerCommandRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "codeengine.execution.backend=docker",
        "codeengine.execution.docker.python-image=python@sha256:d1e9ca7c4e78d1e8ecadb5d44bfc8e956e7a65b659a9950f569f243d72b326d0"
})
@ActiveProfiles("test")
@Transactional
class DockerExecutionEndpointTests {

    private static final String JAVA_ECHO_PROGRAM = "import java.util.Scanner; "
            + "public class Main { public static void main(String[] args) { "
            + "System.out.println(new Scanner(System.in).nextLine()); } }";

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @MockitoSpyBean
    private LocalDockerCommandRunner dockerCommandRunner;

    private final AtomicReference<String> createdContainerId = new AtomicReference<>();

    @BeforeEach
    void verifyIsolatedDatabaseAndCaptureContainerId() throws Exception {
        Assumptions.assumeTrue("true".equals(
                System.getenv("CODEENGINE_RUN_DOCKER_ENDPOINT_TESTS")));
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("/codeengine_test"));
        }
        createdContainerId.set(null);
        doAnswer(invocation -> {
            CodeExecutionResultDTO result = (CodeExecutionResultDTO) invocation.callRealMethod();
            List<String> command = invocation.getArgument(0);
            if (command.size() > 1 && "create".equals(command.get(1))) {
                createdContainerId.set(result.getOutput().trim());
            }
            return result;
        }).when(dockerCommandRunner).run(
                anyList(), nullable(String.class), anyLong(), anyInt(), anyInt());
    }

    @AfterEach
    void verifyContainerWasRemoved() {
        String containerId = createdContainerId.get();
        if (containerId == null || containerId.isBlank()) {
            return;
        }
        CodeExecutionResultDTO result = dockerCommandRunner.run(
                List.of("docker", "inspect", containerId), null, 5, 1024, 1024);
        assertTrue(!"SUCCESS".equals(result.getStatus()),
                "Docker execution container should be removed after the request");
    }

    @Test
    void executesJavaThroughDockerEndpoint() throws Exception {
        execute("java", "public class Main { public static void main(String[] args) { "
                        + "System.out.println(\"docker-java-endpoint\"); } }", null,
                "docker-java-endpoint")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output").value("docker-java-endpoint\n"))
                .andExpect(jsonPath("$.judgeStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.outputCorrect").value(true));
    }

    @Test
    void passesJavaStdinThroughDockerEndpoint() throws Exception {
        execute("java", JAVA_ECHO_PROGRAM, "docker-java-input", "docker-java-input")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output").value("docker-java-input\n"))
                .andExpect(jsonPath("$.outputCorrect").value(true));
    }

    @Test
    void returnsJavaCompileErrorThroughDockerEndpoint() throws Exception {
        execute("java", "public class Main {", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_ERROR"));
    }

    @Test
    void returnsJavaRuntimeErrorThroughDockerEndpoint() throws Exception {
        execute("java", "public class Main { public static void main(String[] args) { "
                        + "throw new RuntimeException(\"java-runtime\"); } }", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNTIME_ERROR"));
    }

    @Test
    void executesPythonThroughDockerEndpoint() throws Exception {
        execute("python", "print('docker-python-endpoint')", null, "docker-python-endpoint")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output").value("docker-python-endpoint\n"))
                .andExpect(jsonPath("$.judgeStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.outputCorrect").value(true));
    }

    @Test
    void passesPythonStdinThroughDockerEndpoint() throws Exception {
        execute("python", "print(input())", "docker-python-input", "docker-python-input")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output").value(
                        org.hamcrest.Matchers.containsString("docker-python-input")))
                .andExpect(jsonPath("$.outputCorrect").value(true));
    }

    @Test
    void returnsPythonRuntimeErrorThroughDockerEndpoint() throws Exception {
        execute("python", "raise RuntimeError('python-runtime')", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNTIME_ERROR"));
    }

    @Test
    void timesOutJavaInDockerAndRemovesContainer() throws Exception {
        String sourceCode = "public class Main { public static void main(String[] args) { "
                + "while (true) { try { Thread.sleep(1000); } "
                + "catch (InterruptedException ignored) {} } } }";

        execute("java", sourceCode, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TIME_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.exitCode").value(-1));
    }

    @Test
    void timesOutPythonInDockerAndRemovesContainer() throws Exception {
        execute("python", "import time\nwhile True:\n    time.sleep(1)", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TIME_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.exitCode").value(-1));
    }

    @Test
    void enforcesJavaDockerOutputLimitAndRemovesContainer() throws Exception {
        String sourceCode = "public class Main { public static void main(String[] args) { "
                + "while (true) { System.out.print(\"x\".repeat(8192)); System.out.flush(); } } }";

        execute("java", sourceCode, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTPUT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.output").value("[OUTPUT LIMIT EXCEEDED]"))
                .andExpect(jsonPath("$.exitCode").value(-1));
    }

    @Test
    void enforcesPythonDockerOutputLimitAndRemovesContainer() throws Exception {
        String sourceCode = "import sys\nwhile True:\n"
                + "    sys.stdout.write('x' * 8192)\n    sys.stdout.flush()";

        execute("python", sourceCode, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTPUT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.output").value("[OUTPUT LIMIT EXCEEDED]"))
                .andExpect(jsonPath("$.exitCode").value(-1));
    }

    @Test
    void prioritizesPythonDockerOutputLimitOverLongRuntime() throws Exception {
        String sourceCode = "import sys, time\nsys.stdout.write('x' * 1048577)\n"
                + "sys.stdout.flush()\ntime.sleep(10)";

        execute("python", sourceCode, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTPUT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.output").value("[OUTPUT LIMIT EXCEEDED]"))
                .andExpect(jsonPath("$.exitCode").value(-1));
    }

    @Test
    void verifiesEffectiveDockerIsolationAndWritableTemporaryWorkspaces() throws Exception {
        String sourceCode = "import os\nfrom pathlib import Path\n"
                + "status = {line.split(':', 1)[0]: line.split(':', 1)[1].strip() "
                + "for line in Path('/proc/self/status').read_text().splitlines() if ':' in line}\n"
                + "assert os.geteuid() == 10001\n"
                + "assert int(status['CapEff'], 16) == 0\n"
                + "assert status['NoNewPrivs'] == '1'\n"
                + "mounts = {}\n"
                + "for line in Path('/proc/self/mountinfo').read_text().splitlines():\n"
                + "    fields = line.split()\n"
                + "    if fields[4] in ('/', '/workspace', '/tmp'):\n"
                + "        mounts[fields[4]] = fields[5].split(',')\n"
                + "assert 'ro' in mounts['/']\n"
                + "assert 'rw' in mounts['/workspace']\n"
                + "assert 'rw' in mounts['/tmp']\n"
                + "for path in (Path('/workspace/docker-probe'), Path('/tmp/docker-probe')):\n"
                + "    path.write_text('ok')\n"
                + "    assert path.read_text() == 'ok'\n"
                + "    path.unlink()\n"
                + "print('docker-isolation-ok')";

        execute("python", sourceCode, null, "docker-isolation-ok")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output").value("docker-isolation-ok\n"))
                .andExpect(jsonPath("$.outputCorrect").value(true));
    }

    @Test
    void verifiesPythonCannotReachHostFilesystemOrNetworkAndRemainsConstrained() throws Exception {
        String sourceCode = "import os, socket, subprocess, uuid\n"
                + "from pathlib import Path\n"
                + "status = {line.split(':', 1)[0]: line.split(':', 1)[1].strip() "
                + "for line in Path('/proc/self/status').read_text().splitlines() if ':' in line}\n"
                + "assert os.geteuid() == 10001\n"
                + "assert int(status['CapEff'], 16) == 0\n"
                + "assert status['NoNewPrivs'] == '1'\n"
                + "assert Path('/sys/fs/cgroup/pids.max').read_text().strip() == '64'\n"
                + "for host_path in ('/run/desktop/mnt/host/c/Windows/win.ini', "
                + "'/host_mnt/c/Windows/win.ini'):\n"
                + "    try:\n        with open(host_path, 'rb') as host_file: host_file.read(1)\n"
                + "    except OSError:\n        pass\n"
                + "    else:\n        raise AssertionError('host file was readable')\n"
                + "for host_dir in ('/run/desktop/mnt/host/c/Windows/Temp', "
                + "'/host_mnt/c/Windows/Temp'):\n"
                + "    host_path = Path(host_dir) / ('codeengine-' + uuid.uuid4().hex + '.tmp')\n"
                + "    try:\n        with host_path.open('xb') as host_file: host_file.write(b'probe')\n"
                + "    except OSError:\n        pass\n"
                + "    else:\n        host_path.unlink(missing_ok=True)\n"
                + "        raise AssertionError('host write was allowed')\n"
                + "root_probe = Path('/etc/codeengine-' + uuid.uuid4().hex)\n"
                + "try:\n    root_probe.write_text('probe')\n"
                + "except OSError:\n    pass\n"
                + "else:\n    root_probe.unlink(missing_ok=True)\n"
                + "    raise AssertionError('read-only root accepted a write')\n"
                + "for workspace in (Path('/workspace'), Path('/tmp')):\n"
                + "    probe = workspace / ('probe-' + uuid.uuid4().hex)\n"
                + "    probe.write_text('ok')\n    assert probe.read_text() == 'ok'\n    probe.unlink()\n"
                + "connection = socket.socket()\nconnection.settimeout(0.5)\n"
                + "try:\n    connection.connect(('192.0.2.1', 80))\n"
                + "except OSError:\n    pass\n"
                + "else:\n    connection.close()\n    raise AssertionError('network connection succeeded')\n"
                + "finally:\n    connection.close()\n"
                + "child = subprocess.run(['sh', '-c', 'exit 0'], timeout=1)\n"
                + "assert child.returncode == 0\nprint('python-boundary-ok')";

        execute("python", sourceCode, null, "python-boundary-ok")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output").value("python-boundary-ok\n"));
    }

    @Test
    void verifiesJavaCannotReachHostFilesystemOrNetworkAndRemainsConstrained() throws Exception {
        String sourceCode = "import java.io.IOException; import java.net.InetSocketAddress; "
                + "import java.net.Socket; import java.nio.file.Files; import java.nio.file.Path; "
                + "import java.util.UUID; "
                + "public class Main { public static void main(String[] args) throws Exception { "
                + "String status = Files.readString(Path.of(\"/proc/self/status\")); "
                + "if (!status.contains(\"Uid:\\t10001\\t10001\")) throw new AssertionError(\"uid\"); "
                + "int capabilityStart = status.indexOf(\"CapEff:\"); "
                + "String capabilities = status.substring(capabilityStart + 7, status.indexOf('\\n', capabilityStart)).trim(); "
                + "if (!capabilities.chars().allMatch(value -> value == '0')) throw new AssertionError(\"capabilities\"); "
                + "if (!status.contains(\"NoNewPrivs:\\t1\")) throw new AssertionError(\"no-new-privileges\"); "
                + "if (!Files.readString(Path.of(\"/sys/fs/cgroup/pids.max\")).trim().equals(\"64\")) "
                + "throw new AssertionError(\"pid-limit\"); "
                + "for (String hostPath : new String[]{\"/run/desktop/mnt/host/c/Windows/win.ini\", "
                + "\"/host_mnt/c/Windows/win.ini\"}) { try { "
                + "Files.newInputStream(Path.of(hostPath)).read(); throw new AssertionError(\"host file readable: \" + hostPath); "
                + "} catch (IOException expected) {} } "
                + "String token = UUID.randomUUID().toString(); "
                + "for (String hostDir : new String[]{\"/run/desktop/mnt/host/c/Windows/Temp\", "
                + "\"/host_mnt/c/Windows/Temp\"}) { Path hostPath = Path.of(hostDir, \"codeengine-\" + token + \".tmp\"); "
                + "try { Files.writeString(hostPath, \"probe\"); Files.deleteIfExists(hostPath); "
                + "throw new AssertionError(\"host write allowed\"); } catch (IOException expected) {} } "
                + "Path rootProbe = Path.of(\"/etc/codeengine-\" + token); try { "
                + "Files.writeString(rootProbe, \"probe\"); Files.deleteIfExists(rootProbe); "
                + "throw new AssertionError(\"read-only root accepted write\"); } catch (IOException expected) {} "
                + "for (String workspace : new String[]{\"/workspace\", \"/tmp\"}) { "
                + "Path probe = Path.of(workspace, \"probe-\" + token); Files.writeString(probe, \"ok\"); "
                + "if (!Files.readString(probe).equals(\"ok\")) throw new AssertionError(); Files.delete(probe); } "
                + "try (Socket socket = new Socket()) { try { socket.connect(new InetSocketAddress(\"192.0.2.1\", 80), 500); "
                + "throw new AssertionError(\"network connection succeeded\"); } catch (IOException expected) {} } "
                + "Process child = new ProcessBuilder(\"sh\", \"-c\", \"exit 0\").start(); "
                + "if (child.waitFor() != 0) throw new AssertionError(); "
                + "System.out.println(\"java-boundary-ok\"); } }";

        execute("java", sourceCode, null, "java-boundary-ok")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output").value("java-boundary-ok\n"));
    }

    @Test
    void rejectsUnsupportedLanguageBeforeCreatingDockerContainer() throws Exception {
        execute("ruby", "puts 'unsupported'", null, null)
                .andExpect(status().isBadRequest());

        assertNull(createdContainerId.get());
    }

    private ResultActions execute(
            String language,
            String sourceCode,
            String input,
            String expectedOutput) throws Exception {
        CodeExecutionRequestDTO request = new CodeExecutionRequestDTO();
        request.setLanguage(language);
        request.setSourceCode(sourceCode);
        request.setInput(input);
        request.setExpectedOutput(expectedOutput);
        return mockMvc.perform(post("/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestContent(request)));
    }

    private String requestContent(CodeExecutionRequestDTO request) {
        return "{\"language\":\"" + escape(request.getLanguage())
                + "\",\"sourceCode\":\"" + escape(request.getSourceCode())
                + "\",\"input\":" + jsonValue(request.getInput())
                + ",\"expectedOutput\":" + jsonValue(request.getExpectedOutput()) + "}";
    }

    private static String jsonValue(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
