package com.jordep.RedirectUrlShortener;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final S3Client s3Client = S3Client.builder().build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String pathParameter = (String) input.get("rawPath"); // pega o UUID da URL encurtada
        String shortUrlCode = pathParameter.replace("/", ""); // remove a barra no uuid

        if (shortUrlCode == null || shortUrlCode.isEmpty()) {
            throw new IllegalArgumentException("Invalid input: 'shortUrlCode' is required");
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder() // mapa que diz onde procurar (bucket) e oq procurar (key)
                .bucket("bucket-urlshortner")
                .key(shortUrlCode + ".json")
                .build();

        InputStream s3ObjectStream;

        try {
            s3ObjectStream = s3Client.getObject(getObjectRequest); // mapeia buscando dentro de getObjectRequest (bucket e key) e o captura em s3ObjectStream
        } catch (Exception exception) {
            throw new RuntimeException("Error fetching data from S3: " + exception.getMessage(), exception);
        }

        UrlData urlData; // pega o s3ObjectStream e transforma em um objeto UrlData
        try {
            urlData = objectMapper.readValue(s3ObjectStream, UrlData.class); // deserializa o s3ObjectStream em um objeto UrlData
        } catch (Exception exception) {
            throw new RuntimeException("Error deserializing URL from S3: " + exception.getMessage(), exception);
        }

        long currentTimeInSeconds = System.currentTimeMillis() / 1000; // pega o tempo atual em segundos

        Map<String, Object> response =  new HashMap<>();

        if (urlData.getExpirationTime() < currentTimeInSeconds) {
            response.put("statusCode", 410);
            response.put("body", "URL has expired");
            return response;
        }
        response.put("statusCode", 302);

        Map<String, String> headers = new HashMap<>();
        headers.put("Location", urlData.getOriginalUrl());
        response.put("headers", headers);
        return response;
    }
}
