package com.programmers.kdt.payment.infrastructure.client.pg;

public class PgClientException extends RuntimeException {

    private final String pgErrorCode;

    public PgClientException(String pgErrorCode, String pgErrorMessage) {
        super(pgErrorMessage);
        this.pgErrorCode = pgErrorCode;
    }

    public String getPgErrorCode() {
        return pgErrorCode;
    }
}
