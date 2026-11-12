package io.pokerbaby.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaToolchainTest {
    @Test
    @DisplayName("Н-9: Gradle executes tests on Java 21")
    void runsOnTheRequiredJavaVersion() {
        assertEquals(21, Runtime.version().feature());
    }
}
