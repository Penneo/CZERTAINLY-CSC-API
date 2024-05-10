package com.czertainly.csc.model.mappers;

import com.czertainly.csc.common.result.ErrorWithDescription;
import com.czertainly.csc.common.result.Result;

public interface SignatureResponseMapper<IN, OUT> {

    Result<OUT, ErrorWithDescription> map(IN model);
}
