package com.prashant.codeengine.dto;

public class LanguageStatisticsDTO {

    private String language;

    private long totalSubmissions;

    private long successfulSubmissions;

    private long compileErrors;

    private long runtimeErrors;

    private long timeLimitExceeded;

    private long outputLimitExceeded;

    private double acceptanceRate;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public long getTotalSubmissions() {
        return totalSubmissions;
    }

    public void setTotalSubmissions(long totalSubmissions) {
        this.totalSubmissions = totalSubmissions;
    }

    public long getSuccessfulSubmissions() {
        return successfulSubmissions;
    }

    public void setSuccessfulSubmissions(long successfulSubmissions) {
        this.successfulSubmissions = successfulSubmissions;
    }

    public long getCompileErrors() {
        return compileErrors;
    }

    public void setCompileErrors(long compileErrors) {
        this.compileErrors = compileErrors;
    }

    public long getRuntimeErrors() {
        return runtimeErrors;
    }

    public void setRuntimeErrors(long runtimeErrors) {
        this.runtimeErrors = runtimeErrors;
    }

    public long getTimeLimitExceeded() {
        return timeLimitExceeded;
    }

    public void setTimeLimitExceeded(long timeLimitExceeded) {
        this.timeLimitExceeded = timeLimitExceeded;
    }

    public long getOutputLimitExceeded() {
        return outputLimitExceeded;
    }

    public void setOutputLimitExceeded(long outputLimitExceeded) {
        this.outputLimitExceeded = outputLimitExceeded;
    }

    public double getAcceptanceRate() {
        return acceptanceRate;
    }

    public void setAcceptanceRate(double acceptanceRate) {
        this.acceptanceRate = acceptanceRate;
    }
}