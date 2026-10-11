package com.crossborderpayment.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes RFC 9457 problem responses from servlet filters (outside Spring MVC).
 * Only fixed, server-defined text is written; never user input or exception messages.
 */
public final class ProblemResponses {

    // Returned when adding / removing a passkey without a recent passkey login
    public static final String REAUTHENTICATION_REQUIRED = "REAUTHENTICATION_REQUIRED";
    public static final String REAUTHENTICATION_REQUIRED_DETAIL = "Please log in with your passkey again to continue.";

    private ProblemResponses() {
    }

    public static ProblemDetail reauthenticationRequired() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, REAUTHENTICATION_REQUIRED_DETAIL);
        problem.setProperty("code", REAUTHENTICATION_REQUIRED);
        return problem;
    }

    static void write(HttpServletResponse response, HttpStatus status, String detail, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        StringBuilder json = new StringBuilder()
                .append("{\"type\":\"about:blank\",\"title\":\"").append(status.getReasonPhrase())
                .append("\",\"status\":").append(status.value())
                .append(",\"detail\":\"").append(detail).append('"');
        if (code != null) {
            json.append(",\"code\":\"").append(code).append('"');
        }
        json.append('}');
        response.getWriter().write(json.toString());
    }
}
