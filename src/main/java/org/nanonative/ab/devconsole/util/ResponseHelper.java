package org.nanonative.ab.devconsole.util;

import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Map;

public class ResponseHelper {
    public static HttpObject responseOk(final HttpObject payload, final String body, ContentType cntType) {
        HttpObject resp = payload.createCorsResponse().statusCode(200).contentType(cntType).body(body);
        return resp;
    }

    public static HttpObject problem(final HttpObject payload, final int status, final String message) {
        HttpObject resp = payload.createCorsResponse().statusCode(status).contentType(ContentType.APPLICATION_PROBLEM_JSON).body(Map.of("message", message, "timestamp", System.currentTimeMillis()));
        return resp;
    }
}
