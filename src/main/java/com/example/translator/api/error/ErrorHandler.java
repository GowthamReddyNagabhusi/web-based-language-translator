package com.example.translator.api.error;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Standardized JSON error response builder. */
public final class ErrorHandler {

    private static final Gson GSON = new Gson();

    private ErrorHandler() {}

    public static void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=UTF-8");
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        body.addProperty("status", status);
        resp.getWriter().write(GSON.toJson(body));
    }

    public static void sendError(HttpServletResponse resp, int status, String message, String requestId) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=UTF-8");
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        body.addProperty("status", status);
        if (requestId != null) body.addProperty("requestId", requestId);
        resp.getWriter().write(GSON.toJson(body));
    }
}
