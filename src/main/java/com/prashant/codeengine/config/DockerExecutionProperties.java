package com.prashant.codeengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "codeengine.execution.docker")
public class DockerExecutionProperties {

    private String javaImage = "";
    private String pythonImage = "";
    private String dockerCommand = "docker";
    private String user = "10001:10001";
    private String cpus = "1";
    private String memory = "256m";
    private int pidsLimit = 64;
    private String workspaceSize = "64m";

    public String getJavaImage() {
        return javaImage;
    }

    public void setJavaImage(String javaImage) {
        this.javaImage = javaImage;
    }

    public String getPythonImage() {
        return pythonImage;
    }

    public void setPythonImage(String pythonImage) {
        this.pythonImage = pythonImage;
    }

    public String getDockerCommand() {
        return dockerCommand;
    }

    public void setDockerCommand(String dockerCommand) {
        this.dockerCommand = dockerCommand;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getCpus() {
        return cpus;
    }

    public void setCpus(String cpus) {
        this.cpus = cpus;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public int getPidsLimit() {
        return pidsLimit;
    }

    public void setPidsLimit(int pidsLimit) {
        this.pidsLimit = pidsLimit;
    }

    public String getWorkspaceSize() {
        return workspaceSize;
    }

    public void setWorkspaceSize(String workspaceSize) {
        this.workspaceSize = workspaceSize;
    }
}
