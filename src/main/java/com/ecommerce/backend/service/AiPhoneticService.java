package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.Item;
import com.ecommerce.backend.repository.ItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPhoneticService {

    private final ItemRepository itemRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Transactional
    public int batchUpdatePhonetics(Long shopId) {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            log.warn("Gemini API key is not configured. Skipping phonetic generation.");
            return 0;
        }

        List<Item> itemsToUpdate = itemRepository.findByShopIdAndNeedsPhoneticUpdateTrue(shopId);
        if (itemsToUpdate.isEmpty()) {
            return 0;
        }

        try {
            // Build the input JSON array
            List<Map<String, Object>> inputList = new ArrayList<>();
            for (Item item : itemsToUpdate) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", item.getId());
                map.put("name", item.getName());
                inputList.add(map);
            }
            String inputJson = objectMapper.writeValueAsString(inputList);

            // Construct prompt
            String prompt = "You are an expert in how offline Google Speech-to-Text misinterprets foreign accents. "
                    + "I have a JSON array of items: " + inputJson + ". "
                    + "For each item, imagine a Nepali person speaking its name into a dumb English offline speech recognition engine. "
                    + "Generate 3-5 exact English string outputs that the engine is most likely to hallucinate and return (e.g., if they say 'jista', it might return 'vista', 'just a', or 'jeesta'). "
                    + "SPECIAL RULE FOR 'A4': If the item name contains 'A4', you MUST include 'if', 'a four', and 'eh four' in the phonetics. "
                    + "Also include direct Nepali translations written in English letters (e.g. 'kalam' for pen). "
                    + "CRITICAL RULE: DO NOT use hyphens or any punctuation. Words must be separated by spaces only (e.g. 'at let e', NOT 'at-let-ee'). "
                    + "Include the original exact name as well. "
                    + "Respond ONLY with a valid JSON array exactly matching this format and nothing else (no markdown blocks, just raw JSON): "
                    + "[{\"id\": 1, \"phonetics\": [\"jeesta\", \"just a\", \"vista\", \"jista\"]}]";

            // Call Gemini API
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + geminiApiKey;
            
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> contentPart = new HashMap<>();
            List<Map<String, String>> parts = new ArrayList<>();
            Map<String, String> textPart = new HashMap<>();
            textPart.put("text", prompt);
            parts.add(textPart);
            contentPart.put("parts", parts);
            contents.add(contentPart);
            requestBody.put("contents", contents);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            String responseString = restTemplate.postForObject(url, request, String.class);
            JsonNode rootNode = objectMapper.readTree(responseString);
            
            // Parse Gemini response
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode partsNode = candidates.get(0).path("content").path("parts");
                if (partsNode.isArray() && partsNode.size() > 0) {
                    String aiResponseText = partsNode.get(0).path("text").asText();
                    // Clean up markdown block if present
                    if (aiResponseText.startsWith("```json")) {
                        aiResponseText = aiResponseText.replaceFirst("```json", "");
                        aiResponseText = aiResponseText.substring(0, aiResponseText.lastIndexOf("```"));
                    }
                    
                    JsonNode responseArray = objectMapper.readTree(aiResponseText.trim());
                    if (responseArray.isArray()) {
                        for (JsonNode node : responseArray) {
                            Long id = node.path("id").asLong();
                            List<String> phonetics = new ArrayList<>();
                            for (JsonNode pNode : node.path("phonetics")) {
                                phonetics.add(pNode.asText());
                            }
                            
                            // Find and update item
                            for (Item item : itemsToUpdate) {
                                if (item.getId().equals(id)) {
                                    item.setPhoneticSpellings(phonetics);
                                    item.setNeedsPhoneticUpdate(false);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            itemRepository.saveAll(itemsToUpdate);
            return itemsToUpdate.size();

        } catch (Exception e) {
            log.error("Failed to batch update phonetics", e);
            throw new RuntimeException("Failed to batch update phonetics via AI", e);
        }
    }
}
