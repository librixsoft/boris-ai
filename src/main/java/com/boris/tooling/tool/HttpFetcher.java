package com.boris.tooling.tool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public interface HttpFetcher {
    HttpResponse<String> send(HttpRequest request, Duration timeout) throws IOException, InterruptedException;
}
