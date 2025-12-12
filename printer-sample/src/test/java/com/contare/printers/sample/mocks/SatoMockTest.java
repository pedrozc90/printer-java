package com.contare.printers.sample.mocks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SatoMockTest {

    @Test
    @DisplayName("Create mock")
    public void createMock() throws IOException {
        try(final SatoMock mock = new SatoMock()) {
            assertEquals("localhost", mock.getHost());
            assertTrue(mock.getPort() != 0);
        }
    }

}
