package com.czertainly.csc.model.signserver;

public record CryptoTokenKey(
        int cryptoTokenId,
        String keyAlias,
        String keyAlgorithm,
        String keySpecification,
        CryptoTokenKeyStatus status) {
}
