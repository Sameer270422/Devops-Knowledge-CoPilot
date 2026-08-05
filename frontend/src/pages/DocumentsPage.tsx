import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';

interface DocumentResponse {
  id: string;
  filename: string;
  sourceType: string;
  status: 'PENDING' | 'INDEXED' | 'FAILED';
  errorMessage: string | null;
  uploadedAt: string;
}

const SOURCE_TYPES = ['RUNBOOK', 'INCIDENT_POSTMORTEM', 'CI_CD_LOG', 'DOCUMENTATION', 'OTHER'];

const statusBadgeClass: Record<DocumentResponse['status'], string> = {
  PENDING: 'badge badge-pending',
  INDEXED: 'badge badge-indexed',
  FAILED: 'badge badge-failed',
};

export function DocumentsPage() {
  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [sourceType, setSourceType] = useState('RUNBOOK');
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fileName, setFileName] = useState('No file chosen');
  const fileInput = useRef<HTMLInputElement>(null);

  const load = () => api.get<DocumentResponse[]>('/api/documents').then(setDocuments).catch(() => undefined);

  useEffect(() => {
    load();
    // Ingestion runs async on the backend — poll while anything is still PENDING so the
    // status column updates without the user having to refresh the page manually.
    const interval = setInterval(() => {
      setDocuments((current) => {
        if (current.some((d) => d.status === 'PENDING')) load();
        return current;
      });
    }, 3000);
    return () => clearInterval(interval);
  }, []);

  const onUpload = async () => {
    const file = fileInput.current?.files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const form = new FormData();
      form.append('file', file);
      form.append('sourceType', sourceType);
      await api.postForm('/api/documents', form);
      if (fileInput.current) fileInput.current.value = '';
      setFileName('No file chosen');
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const onDelete = async (id: string) => {
    await api.delete(`/api/documents/${id}`);
    await load();
  };

  return (
    <div>
      <div className="page-header">
        <h2>Documents</h2>
        <p className="page-subtitle">
          Upload runbooks, incident postmortems, CI/CD logs, or docs (.pdf, .txt, .md, .log, .csv — max
          20MB). Each one is chunked and embedded in the background, then becomes searchable from the
          Chat tab.
        </p>
      </div>

      <div className="card card-pad" style={{ marginBottom: 'var(--space-5)' }}>
        {error && <div className="banner banner-error">{error}</div>}
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
          <select className="input" style={{ width: 'auto' }} value={sourceType} onChange={(e) => setSourceType(e.target.value)}>
            {SOURCE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>

          <button type="button" className="btn btn-secondary" onClick={() => fileInput.current?.click()}>
            Choose file
          </button>
          <span style={{ fontSize: 13.5, color: 'var(--color-text-secondary)' }}>{fileName}</span>
          <input
            ref={fileInput}
            type="file"
            style={{ display: 'none' }}
            onChange={(e) => setFileName(e.target.files?.[0]?.name ?? 'No file chosen')}
          />

          <button className="btn btn-primary" onClick={onUpload} disabled={uploading} style={{ marginLeft: 'auto' }}>
            {uploading ? 'Uploading…' : 'Upload'}
          </button>
        </div>
      </div>

      <div className="card">
        <table className="table">
          <thead>
            <tr>
              <th>Filename</th>
              <th>Source</th>
              <th>Status</th>
              <th>Uploaded</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {documents.map((d) => (
              <tr key={d.id}>
                <td>{d.filename}</td>
                <td style={{ color: 'var(--color-text-secondary)' }}>{d.sourceType}</td>
                <td>
                  <span className={statusBadgeClass[d.status]} title={d.errorMessage ?? ''}>
                    {d.status}
                  </span>
                </td>
                <td style={{ color: 'var(--color-text-secondary)' }}>{new Date(d.uploadedAt).toLocaleString()}</td>
                <td>
                  <button className="btn btn-ghost" onClick={() => onDelete(d.id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {documents.length === 0 && <div className="empty-state">No documents yet. Upload one above to get started.</div>}
      </div>
    </div>
  );
}
