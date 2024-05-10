package com.czertainly.csc.clients.signserver;

public record BatchSignatureWithValidationData(BatchSignaturesResponse signatureData, ValidationData validationData) {
}