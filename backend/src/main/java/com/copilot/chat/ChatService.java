package com.copilot.chat;

import com.copilot.chat.dto.CitationResponse;
import com.copilot.chat.rag.PromptBuilder;
import com.copilot.chat.rag.RetrievalService;
import com.copilot.common.ResourceNotFoundException;
import com.copilot.llm.ChatLlmClient;
import com.copilot.vector.RetrievedChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final ChatLlmClient chatLlmClient;

    public ChatService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            RetrievalService retrievalService,
            PromptBuilder promptBuilder,
            ChatLlmClient chatLlmClient
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.chatLlmClient = chatLlmClient;
    }

    @Transactional
    public ChatSession createSession(UUID userId, String title) {
        return sessionRepository.save(new ChatSession(userId, title));
    }

    public List<ChatSession> listSessions(UUID userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<ChatMessage> getMessages(UUID userId, UUID sessionId) {
        requireOwnedSession(userId, sessionId);
        return messageRepository.findBySessionIdOrderByCreatedAt(sessionId);
    }

    public record AskResult(ChatMessage assistantMessage, List<CitationResponse> citations) {}

    @Transactional
    public AskResult ask(UUID userId, UUID sessionId, String question) {
        requireOwnedSession(userId, sessionId);

        messageRepository.save(new ChatMessage(sessionId, MessageRole.USER, question, List.of()));

        // Retrieval is scoped to this user's own documents only (see RetrievalService /
        // VectorStore) — the RAG pipeline itself is the enforcement point for "can't see
        // another user's data," not an afterthought filter on the result set.
        List<RetrievedChunk> context = retrievalService.retrieve(userId, question);

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userMessage = promptBuilder.buildUserMessage(question, context);
        String answer = chatLlmClient.generate(systemPrompt, userMessage);

        List<UUID> citedChunkIds = context.stream().map(RetrievedChunk::chunkId).toList();
        ChatMessage assistantMessage = messageRepository.save(
                new ChatMessage(sessionId, MessageRole.ASSISTANT, answer, citedChunkIds));

        List<CitationResponse> citations = dedupeByDocument(context);

        return new AskResult(assistantMessage, citations);
    }

    private void requireOwnedSession(UUID userId, UUID sessionId) {
        sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
    }

    private String excerpt(String content) {
        String trimmed = content.strip();
        return trimmed.length() > 220 ? trimmed.substring(0, 220) + "…" : trimmed;
    }

    // Retrieval commonly returns several chunks from the same document (e.g. multiple
    // paragraphs of one postmortem all being relevant), which previously showed as the
    // same filename repeated once per chunk in the UI. One citation per unique document
    // instead, keeping the first (closest-matching, since context is already ordered by
    // similarity) chunk's excerpt as the representative one.
    private List<CitationResponse> dedupeByDocument(List<RetrievedChunk> context) {
        Map<UUID, CitationResponse> byDocument = new LinkedHashMap<>();
        for (RetrievedChunk c : context) {
            byDocument.putIfAbsent(c.documentId(), new CitationResponse(c.documentId(), c.documentFilename(), excerpt(c.content())));
        }
        return List.copyOf(byDocument.values());
    }
}
