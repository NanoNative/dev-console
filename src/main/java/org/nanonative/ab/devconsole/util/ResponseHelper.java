package org.nanonative.ab.devconsole.util;

import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Map;

public class ResponseHelper {

    private ResponseHelper() {}

    public static HttpObject responseOk(final HttpObject payload, final String body, ContentType cntType) {
        HttpObject resp = payload.createCorsResponse().statusCode(200).contentType(cntType).body(body);
        return resp;
    }

    public static HttpObject problem(final HttpObject payload, final int status, final String message) {
        HttpObject resp = payload.createCorsResponse().statusCode(status).contentType(ContentType.APPLICATION_PROBLEM_JSON).body(Map.of("message", message, "timestamp", System.currentTimeMillis()));
        return resp;
    }

    public static ContentType getTypeFromFileExt(String path) {
        if (!path.contains(".")) {
            return ContentType.TEXT_PLAIN;
        }
        String ext = path.substring(path.lastIndexOf('.') + 1);
        return switch (ext) {
            case "html" -> ContentType.TEXT_HTML;
            case "css" -> ContentType.TEXT_CSS;
            case "js" -> ContentType.APPLICATION_JAVASCRIPT;
            default -> ContentType.TEXT_PLAIN;
        };
    }
}
