package com.crossborderpayment.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Runs against the real embedded server, because MockMvc does not perform the ERROR dispatch to /error
 * that Spring Boot uses to render error responses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchTests {

    @LocalServerPort
    private int port;

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    @Test
    void missingCsrfTokenIsReportedAs403NotAs401() throws Exception {
        HttpResponse<String> response = send(request("/api/auth/recovery")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"emailAddr\":\"taro@example.com\"}")));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointIsStill401() throws Exception {
        assertThat(send(request("/api/passkeys").GET()).statusCode()).isEqualTo(401);
    }

    @Test
    void errorResponseDoesNotExposeInternalDetails() throws Exception {
        HttpResponse<String> response = send(request("/api/auth/recovery")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(response.body()).doesNotContainIgnoringCase("exception").doesNotContain("trace", "csrf");
    }
}
