package com.czertainly.csc.signing;

import com.czertainly.csc.signing.configuration.SignaturePackaging;

public record Signature(
        byte[] value,
        SignaturePackaging packaging
) {
}
