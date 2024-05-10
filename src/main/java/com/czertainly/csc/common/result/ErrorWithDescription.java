package com.czertainly.csc.common.result;

public record ErrorWithDescription(String error, String description) implements ErrorValue {
}
