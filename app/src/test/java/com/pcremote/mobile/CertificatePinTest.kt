package com.pcremote.mobile

import org.junit.Assert.*
import org.junit.Test

class CertificatePinTest {
    @Test fun knownSha256() {
        assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",CertificatePin.digest("abc".toByteArray()))
    }
    @Test fun substitutedCertificateRejected() {
        val cert="original certificate bytes".toByteArray()
        assertTrue(CertificatePin.matches(cert,CertificatePin.digest(cert)))
        assertFalse(CertificatePin.matches("attacker certificate".toByteArray(),CertificatePin.digest(cert)))
        assertFalse(CertificatePin.matches(cert,""))
        assertFalse(CertificatePin.matches(cert,CertificatePin.digest(cert).take(12)))
    }
}
