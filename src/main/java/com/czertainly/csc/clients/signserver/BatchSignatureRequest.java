package com.czertainly.csc.clients.signserver;

public record BatchSignatureRequest(String data, String hashingAlgorithm, String customIdentifier) {
}
