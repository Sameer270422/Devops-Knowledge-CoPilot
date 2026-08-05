package com.copilot.llm;

/** Sends a constructed RAG prompt to the configured LLM and returns its answer text. */
public interface ChatLlmClient {
    String generate(String systemPrompt, String userMessage);
}
