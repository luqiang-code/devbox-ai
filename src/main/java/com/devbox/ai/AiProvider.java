package com.devbox.ai;

/**
 * Abstraction over LLM providers (Anthropic, DeepSeek, etc.).
 */
public interface AiProvider {
    /**
     * Send a prompt and get the response text.
     *
     * @param systemPrompt system-level instruction (can be null)
     * @param userPrompt   the user message
     * @param maxTokens    max tokens in response
     * @return the model's text response
     */
    String chat(String systemPrompt, String userPrompt, int maxTokens) throws Exception;

    /** Short name for logging, e.g. "DeepSeek" or "Claude". */
    String providerName();
}
