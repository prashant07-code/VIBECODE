package com.prashant.codeengine.service;

import org.springframework.stereotype.Service;

@Service
public class OutputEvaluator {

    public boolean isOutputCorrect(
            String actualOutput,
            String expectedOutput) {

        if (actualOutput == null) {
            actualOutput = "";
        }

        if (expectedOutput == null) {
            expectedOutput = "";
        }

        return actualOutput.trim()
                .equals(expectedOutput.trim());
    }
}