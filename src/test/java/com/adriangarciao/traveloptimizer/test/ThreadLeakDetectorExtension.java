package com.adriangarciao.traveloptimizer.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

public class ThreadLeakDetectorExtension implements AfterAllCallback {

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ThreadLeakDetector: Non-daemon threads at suite end ===\n");

        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        int count = 0;
        for (Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
            Thread t = e.getKey();
            if (t == Thread.currentThread()) continue;
            if (t.isDaemon()) continue;
            // Skip common JUnit/Testcontainers threads that are expected (we will still print them)
            count++;
            sb.append("--- Thread: ").append(t.getName()).append(" (id=").append(t.getId()).append(")\n");
            for (StackTraceElement st : e.getValue()) {
                sb.append("    at ").append(st.toString()).append("\n");
            }
        }

        sb.append("=== ThreadLeakDetector: total non-daemon threads: ").append(count).append(" ===\n");

        if (count > 0) {
            // Print to stderr to ensure visibility in Surefire console output
            System.err.println(sb.toString());
        } else {
            System.out.println(sb.toString());
        }
    }
}
