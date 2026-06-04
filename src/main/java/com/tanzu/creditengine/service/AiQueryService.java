package com.tanzu.creditengine.service;

/**
 * Answers operator questions about credit data in natural language. Backed by Spring AI
 * tool-calling against the Tanzu {@code credit-chat} GenAI service in the cloud profile,
 * and by a deterministic stub in the local profile.
 */
public interface AiQueryService {

    AiAnswer answer(String prompt);
}
