package com.czertainly.csc.model.mappers;

import com.czertainly.csc.api.auth.SignatureActivationData;
import com.czertainly.csc.common.result.ErrorWithDescription;
import com.czertainly.csc.common.result.Result;

public interface SignatureRequestMapper<IN, OUT> {

    Result<OUT, ErrorWithDescription> map(IN dto, SignatureActivationData sad);
}
