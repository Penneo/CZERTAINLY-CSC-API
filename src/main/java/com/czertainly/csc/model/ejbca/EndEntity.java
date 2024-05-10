package com.czertainly.csc.model.ejbca;

public record EndEntity(String username, String password, String subjectDN, String san) {
}
