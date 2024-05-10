package com.czertainly.csc.clients.signserver;

public record EncodedValidationDataWrapper(String signatureData, ValidationData validationData) {
}