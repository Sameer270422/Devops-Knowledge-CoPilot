package com.copilot.chat;

import com.copilot.auth.CurrentUser;
import com.copilot.chat.dto.*;
import com.copilot.common.RateLimiter;
import com.copilot.common.TooManyRequestsException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    // Per the security plan's "rate limiting on the API and specifically the chat
    // endpoint" — each ask() call makes a real, billed LLM request, so this is the
    // highest-value place in the whole app to cap abuse/runaway usage.
    private static final int ASK_MAX_PER_WINDOW = 20;
    private static final long ASK_WINDOW_SECONDS = 60;

    private final ChatService chatService;
    private final RateLimiter rateLimiter;

    public ChatController(ChatService chatService, RateLimiter rateLimiter) {
        this.chatService = chatService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ChatSessionResponse> createSession(@RequestBody(required = false) CreateSessionRequest request) {
        String title = request != null ? request.title() : null;
        ChatSession session = chatService.createSession(CurrentUser.get().id(), title);
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatSessionResponse.from(session));
    }

    @GetMapping("/sessions")
    public List<ChatSessionResponse> listSessions() {
        return chatService.listSessions(CurrentUser.get().id()).stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    @GetMapping("/sessions/{id}/messages")
    public List<ChatMessageResponse> getMessages(@PathVariable UUID id) {
        return chatService.getMessages(CurrentUser.get().id(), id).stream()
                // History reload doesn't re-resolve citation excerpts — cited_chunk_ids is
                // stored, but hydrating full CitationResponse objects for old messages would
                // mean re-joining against chunks that may since have been deleted. The
                // ids are preserved for that future enhancement; empty list is a safe default.
                .map(m -> ChatMessageResponse.from(m, List.of()))
                .toList();
    }

    @PostMapping("/sessions/{id}/messages")
    public ChatMessageResponse ask(@PathVariable UUID id, @Valid @RequestBody CreateMessageRequest request) {
        UUID userId = CurrentUser.get().id();
        if (!rateLimiter.allow("chat:" + userId, ASK_MAX_PER_WINDOW, ASK_WINDOW_SECONDS)) {
            throw new TooManyRequestsException("Too many questions — please wait a moment and try again.");
        }
        ChatService.AskResult result = chatService.ask(userId, id, request.content());
        return ChatMessageResponse.from(result.assistantMessage(), result.citations());
    }
}
