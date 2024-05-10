package com.czertainly.csc.signing.filter;

public interface Criterion<T> {

    boolean matches(T element);

}
