package com.keshore.webhookapp;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.logging.Logger;

@Component
public class AppStartupRunner implements ApplicationRunner {

    private static final Logger logger = Logger.getLogger(AppStartupRunner.class.getName());

    @Override
    public void run(ApplicationArguments args) throws Exception {
        RestTemplate restTemplate = new RestTemplate();

        // Step 1: Call /generateWebhook
        String url = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";

        Map<String, String> reqBody = new HashMap<>();
        reqBody.put("name", "John Doe");
        reqBody.put("regNo", "REG12347");
        reqBody.put("email", "john@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(reqBody, headers);

        ResponseEntity<JsonNode> response;
        try {
            response = restTemplate.postForEntity(url, entity, JsonNode.class);
        } catch (RestClientException e) {
            logger.severe("Error calling /generateWebhook: " + e.getMessage());
            return;
        }

        JsonNode json = response.getBody();
        if (json == null || !json.has("webhook") || !json.has("accessToken") || !json.has("data")) {
            logger.severe("Invalid response body or missing fields in /generateWebhook response");
            return;
        }

        String webhook = json.get("webhook").asText();
        String token = json.get("accessToken").asText();
        JsonNode users = json.get("data").get("users");

        // Step 2: Build follow map
        Map<Integer, Set<Integer>> map = new HashMap<>();
        for (JsonNode user : users) {
            int id = user.get("id").asInt();
            JsonNode follows = user.get("follows");
            Set<Integer> set = new HashSet<>();
            for (JsonNode f : follows) {
                set.add(f.asInt());
            }
            map.put(id, set);
        }

        // Step 3: Find mutuals
        List<List<Integer>> result = new ArrayList<>();
        for (Map.Entry<Integer, Set<Integer>> entry : map.entrySet()) {
            int a = entry.getKey();
            for (int b : entry.getValue()) {
                if (map.containsKey(b) && map.get(b).contains(a) && a < b) {
                    result.add(Arrays.asList(a, b));
                }
            }
        }

        // Step 4: Send result to webhook with token
        HttpHeaders resultHeaders = new HttpHeaders();
        resultHeaders.setContentType(MediaType.APPLICATION_JSON);
        resultHeaders.set("Authorization", token);

        Map<String, Object> finalBody = new HashMap<>();
        finalBody.put("regNo", "REG12347");
        finalBody.put("outcome", result);

        HttpEntity<Map<String, Object>> resultEntity = new HttpEntity<>(finalBody, resultHeaders);

        int tries = 0;
        while (tries < 4) {
            try {
                restTemplate.postForEntity(webhook, resultEntity, String.class);
                logger.info("✅ Sent to webhook!");
                break;
            } catch (RestClientException e) {
                tries++;
                logger.warning("❌ Failed attempt #" + tries + ": " + e.getMessage());
                Thread.sleep(1000);
            }
        }

        if (tries == 4) {
            logger.severe("❌ All attempts failed after 4 retries.");
        }
    }
}
