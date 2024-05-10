package com.czertainly.csc.signing.filter;

import com.czertainly.csc.model.signserver.CryptoToken;

public record Worker(String workerName, int workerId, CryptoToken cryptoToken) {
}
