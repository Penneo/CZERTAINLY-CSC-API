package com.czertainly.csc.clients.signserver;

public record BatchSignatureResponse(String signature, String hash, String customIdentifier) {
}
