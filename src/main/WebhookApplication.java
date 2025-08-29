package com.example.webhooks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

@SpringBootApplication
public class WebhookApplication implements CommandLineRunner {

    @Value("${app.name}")
    private String name;
    @Value("${app.regNo}")
    private String regNo;
    @Value("${app.email}")
    private String email;

    public static void main(String[] args) {
        SpringApplication.run(WebhookApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    private String readResourceFile(String path) throws Exception {
        ClassPathResource res = new ClassPathResource(path);
        try (InputStream is = res.getInputStream(); Scanner s = new Scanner(is, StandardCharsets.UTF_8.name())) {
            s.useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }

    @Override
    public void run(String... args) throws Exception {
        RestTemplate rt = restTemplate();

        // 1) call generateWebhook
        String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> requestBody = Map.of(
                "name", name,
                "regNo", regNo,
                "email", email
        );
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        System.out.println("Calling generateWebhook...");
        ResponseEntity<Map> generateResp = rt.postForEntity(generateUrl, entity, Map.class);
        if (generateResp.getStatusCode() != HttpStatus.OK && generateResp.getStatusCode() != HttpStatus.CREATED) {
            System.err.println("generateWebhook returned: " + generateResp.getStatusCode());
            System.exit(1);
        }
        Map bodyMap = generateResp.getBody();
        // try common keys:
        String webhookUrl = (bodyMap.get("webhook") != null) ? bodyMap.get("webhook").toString() : null;
        String accessToken = (bodyMap.get("accessToken") != null) ? bodyMap.get("accessToken").toString() : null;
        // fallback if nested:
        if (webhookUrl == null && bodyMap.get("data") instanceof Map) {
            Map data = (Map) bodyMap.get("data");
            webhookUrl = data.get("webhook") != null ? data.get("webhook").toString() : webhookUrl;
            accessToken = data.get("accessToken") != null ? data.get("accessToken").toString() : accessToken;
        }

        System.out.println("Received webhook: " + webhookUrl);
        System.out.println("Received accessToken: [hidden]");

        // 2) Determine which answer file to read based on regNo last two digits
        String lastTwo = regNo.length() >= 2 ? regNo.substring(regNo.length()-2) : regNo;
        int lastNum = 0;
        try { lastNum = Integer.parseInt(lastTwo); } catch(Exception ignored){}
        boolean odd = (lastNum % 2 == 1);

        String answerFile = odd ? "answer_q1.sql" : "answer_q2.sql";
        System.out.println("Based on regNo reading: " + answerFile);

        String finalQuery = readResourceFile(answerFile).trim();
        if (finalQuery.isEmpty()) {
            System.err.println("Final query is empty. Put your final SQL in src/main/resources/" + answerFile);
            System.exit(1);
        }

        // 3) Submit finalQuery to testWebhook endpoint
        String testUrl = "https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA";
        HttpHeaders submitHeaders = new HttpHeaders();
        submitHeaders.setContentType(MediaType.APPLICATION_JSON);
        // IMPORTANT: the assignment says Authorization: <accessToken>
        submitHeaders.set("Authorization", accessToken);

        Map<String, String> submitBody = Map.of("finalQuery", finalQuery);
        HttpEntity<Map<String, String>> submitEntity = new HttpEntity<>(submitBody, submitHeaders);

        System.out.println("Submitting finalQuery to testWebhook...");
        ResponseEntity<String> submitResp = rt.postForEntity(testUrl, submitEntity, String.class);
        System.out.println("Submission response: " + submitResp.getStatusCode());
        System.out.println("Response body: " + submitResp.getBody());

        // exit so the app doesn't keep running
        System.exit(0);
    }
}

