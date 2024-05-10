package com.czertainly.csc.signing.configuration;

import com.czertainly.csc.signing.filter.Worker;

public record WorkerWithCapabilities(Worker worker, WorkerCapabilities capabilities) {

}
