package com.example.b;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class AlwaysFailsTest {

    @Test
    void intentionallyFails() {
        fail("intentional failure to verify nothing gets published on a failed reactor build");
    }
}
