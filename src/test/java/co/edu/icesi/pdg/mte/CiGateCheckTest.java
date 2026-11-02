package co.edu.icesi.pdg.mte;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class CiGateCheckTest {

    @Test
    void failsOnPurposeToCheckDeployIsBlocked() {
        fail("Intentional failure: a red CI run must not reach staging");
    }
}
