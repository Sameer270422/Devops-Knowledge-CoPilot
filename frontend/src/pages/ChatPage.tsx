import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';

interface ChatSession {
  id: string;
  title: string;
  createdAt: string;
}

interface Citation {
  documentId: string;
  filename: string;
  excerpt: string;
}

interface ChatMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations: Citation[];
  createdAt: string;
}

// Belt-and-suspenders alongside the backend dedup fix in ChatService — the API can
// retrieve several chunks from the same document, so without this the same filename
// would show once per chunk instead of once per source.
function uniqueSources(citations: Citation[]): string[] {
  return Array.from(new Set(citations.map((c) => c.filename)));
}

export function ChatPage() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState('');
  const [asking, setAsking] = useState(false);

  const loadSessions = () => api.get<ChatSession[]>('/api/chat/sessions').then(setSessions);

  useEffect(() => {
    loadSessions();
  }, []);

  const openSession = async (id: string) => {
    setActiveSessionId(id);
    const msgs = await api.get<ChatMessage[]>(`/api/chat/sessions/${id}/messages`);
    setMessages(msgs);
  };

  const newSession = async () => {
    const session = await api.post<ChatSession>('/api/chat/sessions', { title: null });
    await loadSessions();
    setActiveSessionId(session.id);
    setMessages([]);
  };

  const onAsk = async (e: FormEvent) => {
    e.preventDefault();
    if (!question.trim()) return;
    let sessionId = activeSessionId;
    if (!sessionId) {
      const session = await api.post<ChatSession>('/api/chat/sessions', { title: question.slice(0, 60) });
      await loadSessions();
      sessionId = session.id;
      setActiveSessionId(sessionId);
    }

    const userMsg: ChatMessage = {
      id: crypto.randomUUID(), role: 'USER', content: question, citations: [], createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, userMsg]);
    setQuestion('');
    setAsking(true);
    try {
      const reply = await api.post<ChatMessage>(`/api/chat/sessions/${sessionId}/messages`, { content: userMsg.content });
      setMessages((m) => [...m, reply]);
    } catch (err) {
      setMessages((m) => [...m, {
        id: crypto.randomUUID(), role: 'ASSISTANT',
        content: err instanceof Error ? `Error: ${err.message}` : 'Something went wrong.',
        citations: [], createdAt: new Date().toISOString(),
      }]);
    } finally {
      setAsking(false);
    }
  };

  return (
    <div className="chat-layout">
      <aside>
        <button className="btn btn-secondary btn-block" style={{ marginBottom: 'var(--space-3)' }} onClick={newSession}>
          + New chat
        </button>
        {sessions.map((s) => (
          <div
            key={s.id}
            className={`chat-sidebar-item${s.id === activeSessionId ? ' active' : ''}`}
            onClick={() => openSession(s.id)}
          >
            {s.title}
          </div>
        ))}
      </aside>

      <section className="card chat-window">
        <div className="chat-messages">
          {messages.length === 0 && (
            <div className="empty-state">Ask a question about your uploaded documents.</div>
          )}
          {messages.map((m) => {
            const sources = uniqueSources(m.citations);
            const isUser = m.role === 'USER';
            return (
              <div key={m.id} className={`chat-bubble-row ${isUser ? 'user' : 'assistant'}`}>
                <div className={`chat-bubble ${isUser ? 'user' : 'assistant'}`}>{m.content}</div>
                {sources.length > 0 && (
                  <div className="chat-sources">Sources: {sources.join(', ')}</div>
                )}
              </div>
            );
          })}
          {asking && (
            <div className="chat-bubble-row assistant">
              <div className="chat-bubble assistant" style={{ color: 'var(--color-text-muted)' }}>Thinking…</div>
            </div>
          )}
        </div>

        <form onSubmit={onAsk} className="chat-input-row">
          <input
            className="input"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Ask about your documents…"
          />
          <button type="submit" className="btn btn-primary" disabled={asking}>Send</button>
        </form>
      </section>
    </div>
  );
}
