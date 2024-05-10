package com.czertainly.csc.model;

import com.czertainly.csc.signing.Signature;

import java.util.List;
import java.util.Set;

public record SignedDocuments(
    List<Signature> signatures,
    Set<String> crls,
    Set<String> ocsps,
    Set<String> certs
){
}
