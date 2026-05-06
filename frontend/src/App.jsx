import { useEffect, useRef, useState } from "react";
import "./App.css";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

function App() {
  const [token, setToken] = useState(localStorage.getItem("token"));
  const [authMode, setAuthMode] = useState("login");
  const [authForm, setAuthForm] = useState({
    name: "",
    email: "",
    password: "",
  });

  const [file, setFile] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [selectedDoc, setSelectedDoc] = useState(null);
  const [mediaUrl, setMediaUrl] = useState(null);
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState("");
  const [timestamp, setTimestamp] = useState(null);
  const [summary, setSummary] = useState("");
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState(null);

  const mediaRef = useRef(null);

  const showToast = (message, type = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  useEffect(() => {
    if (token) {
      loadDocuments();
    }
  }, [token]);

  useEffect(() => {
    return () => {
      if (mediaUrl) {
        URL.revokeObjectURL(mediaUrl);
      }
    };
  }, [mediaUrl]);

  const authHeaders = () => {
    return token ? { Authorization: `Bearer ${token}` } : {};
  };

  const loadProtectedMedia = async (documentId) => {
    try {
      if (mediaUrl) {
        URL.revokeObjectURL(mediaUrl);
      }

      const res = await fetch(`${API_BASE}/documents/${documentId}/file`, {
        headers: {
          ...authHeaders(),
        },
      });

      if (!res.ok) {
        setMediaUrl(null);
        showToast("Unable to load media file", "error");
        return;
      }

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);

      setMediaUrl(url);
    } catch (error) {
      console.error(error);
      setMediaUrl(null);
      showToast("Media loading failed", "error");
    }
  };

  const handleAuth = async () => {
    try {
      setLoading(true);

      const endpoint =
        authMode === "login"
          ? `${API_BASE}/auth/login`
          : `${API_BASE}/auth/register`;

      const body =
        authMode === "login"
          ? {
              email: authForm.email,
              password: authForm.password,
            }
          : authForm;

      const res = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      });

      const data = await res.json();

      if (authMode === "register") {
        if (!data.token && data.message !== "Registration successful") {
          showToast(data.message || "Registration failed", "error");
          return;
        }

        showToast("Registration successful. Please login now.", "success");

        setAuthMode("login");
        setAuthForm({
          name: "",
          email: authForm.email,
          password: "",
        });

        return;
      }

      if (!data.token) {
        showToast(data.message || "Login failed", "error");
        return;
      }

      localStorage.setItem("token", data.token);
      setToken(data.token);

      showToast(data.message || "Login successful", "success");
    } catch (error) {
      console.error(error);
      showToast("Authentication failed", "error");
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    if (mediaUrl) {
      URL.revokeObjectURL(mediaUrl);
    }

    localStorage.removeItem("token");
    setToken(null);
    setDocuments([]);
    setSelectedDoc(null);
    setMediaUrl(null);
    setAnswer("");
    setSummary("");
    setTimestamp(null);
    setQuestion("");

    showToast("Logged out successfully", "success");
  };

  const loadDocuments = async () => {
    try {
      const res = await fetch(`${API_BASE}/documents`, {
        headers: {
          ...authHeaders(),
        },
      });

      const data = await res.json();

      if (!res.ok) {
        showToast(
          data.message || data.error || "Failed to load documents",
          "error"
        );
        return;
      }

      setDocuments(data);
    } catch (error) {
      console.error("Failed to load documents", error);
      showToast("Failed to load documents", "error");
    }
  };

  const uploadFile = async () => {
    if (!file) {
      showToast("Please select a file first", "error");
      return;
    }

    const formData = new FormData();
    formData.append("file", file);

    try {
      setLoading(true);

      const res = await fetch(`${API_BASE}/documents/upload`, {
        method: "POST",
        headers: {
          ...authHeaders(),
        },
        body: formData,
      });

      const data = await res.json();

      if (!res.ok) {
        showToast(data.message || data.error || "Upload failed", "error");
        return;
      }

      setSelectedDoc(data);
      setFile(null);
      setAnswer("");
      setSummary("");
      setTimestamp(null);
      setQuestion("");
      setMediaUrl(null);

      if (
        data.fileType?.toUpperCase() === "AUDIO" ||
        data.fileType?.toUpperCase() === "VIDEO"
      ) {
        await loadProtectedMedia(data.id);
      }

      await loadDocuments();

      showToast("File uploaded successfully", "success");
    } catch (error) {
      console.error(error);
      showToast("Upload failed", "error");
    } finally {
      setLoading(false);
    }
  };

  const selectDocument = async (doc) => {
    setSelectedDoc(doc);
    setAnswer("");
    setSummary("");
    setTimestamp(null);
    setQuestion("");
    setMediaUrl(null);

    if (
      doc.fileType?.toUpperCase() === "AUDIO" ||
      doc.fileType?.toUpperCase() === "VIDEO"
    ) {
      await loadProtectedMedia(doc.id);
    }
  };

  const askQuestion = async () => {
    if (!selectedDoc) {
      showToast("Please select a document first", "error");
      return;
    }

    if (!question.trim()) {
      showToast("Please type a question", "error");
      return;
    }

    try {
      setLoading(true);
      setAnswer("");
      setTimestamp(null);

      const res = await fetch(`${API_BASE}/chat/${selectedDoc.id}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders(),
        },
        body: JSON.stringify({
          question: question,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        setAnswer(data.message || data.error || "Backend error occurred");
        setTimestamp(null);
        showToast("AI response failed", "error");
        return;
      }

      setAnswer(data.answer);

      if (data.timestamp !== null && data.timestamp !== undefined) {
        setTimestamp(Number(data.timestamp));
      } else {
        setTimestamp(null);
      }

      showToast("AI answer generated", "success");
    } catch (error) {
      console.error(error);
      showToast("Chat failed", "error");
    } finally {
      setLoading(false);
    }
  };

  const askQuestionStreaming = async () => {
    if (!selectedDoc) {
      showToast("Please select a document first", "error");
      return;
    }

    if (!question.trim()) {
      showToast("Please type a question", "error");
      return;
    }

    try {
      setLoading(true);
      setAnswer("");
      setTimestamp(null);

      const res = await fetch(`${API_BASE}/chat/${selectedDoc.id}/stream`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders(),
        },
        body: JSON.stringify({
          question: question,
        }),
      });

      if (!res.ok || !res.body) {
        const errorText = await res.text();
        setAnswer(errorText || "AI streaming failed. Please try again later.");
        showToast("AI streaming failed", "error");
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();

      let finalText = "";

      while (true) {
        const { value, done } = await reader.read();

        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        finalText += chunk;

        if (finalText.includes("[[TIMESTAMP:")) {
          const parts = finalText.split("[[TIMESTAMP:");
          const cleanAnswer = parts[0].trim();

          const timestampPart = parts[1]?.replace("]]", "").trim();

          setAnswer(cleanAnswer);

          if (
            timestampPart &&
            timestampPart !== "null" &&
            timestampPart !== "undefined" &&
            !Number.isNaN(Number(timestampPart))
          ) {
            setTimestamp(Number(timestampPart));
          }
        } else {
          setAnswer(finalText);
        }
      }

      if (finalText.toLowerCase().includes("ai streaming failed")) {
        showToast("AI streaming failed. Check backend console.", "error");
      } else {
        showToast("Streaming completed", "success");
      }
    } catch (error) {
      console.error(error);
      showToast("Chat streaming failed", "error");
    } finally {
      setLoading(false);
    }
  };

  const getSummary = async () => {
    if (!selectedDoc) {
      showToast("Please select a document first", "error");
      return;
    }

    try {
      setLoading(true);

      const res = await fetch(`${API_BASE}/summary/${selectedDoc.id}`, {
        method: "POST",
        headers: {
          ...authHeaders(),
        },
      });

      const data = await res.json();

      if (!res.ok) {
        setSummary(data.message || data.error || "Backend error occurred");
        showToast("Summary failed", "error");
        return;
      }

      setSummary(data.answer);
      showToast("Summary generated successfully", "success");
    } catch (error) {
      console.error(error);
      showToast("Summary failed", "error");
    } finally {
      setLoading(false);
    }
  };

  const selectedFileType = selectedDoc?.fileType?.toUpperCase();

  const isAudio = selectedFileType === "AUDIO";
  const isVideo = selectedFileType === "VIDEO";
  const isMedia = isAudio || isVideo;

  const hasTimestamp =
    timestamp !== null &&
    timestamp !== undefined &&
    !Number.isNaN(Number(timestamp));

  const playFromTimestamp = () => {
    if (!mediaRef.current || !hasTimestamp) {
      showToast("No timestamp found for this answer", "error");
      return;
    }

    mediaRef.current.currentTime = Number(timestamp);
    mediaRef.current.play();

    showToast(`Playing from ${formatTime(timestamp)}`, "success");
  };

  const getFileIcon = (type) => {
    const upperType = type?.toUpperCase();

    if (upperType === "PDF") return "📄";
    if (upperType === "AUDIO") return "🎵";
    if (upperType === "VIDEO") return "🎬";

    return "📁";
  };

  const formatTime = (seconds) => {
    if (
      seconds === null ||
      seconds === undefined ||
      Number.isNaN(Number(seconds))
    ) {
      return "--:--";
    }

    const time = Number(seconds);
    const mins = Math.floor(time / 60);
    const secs = Math.floor(time % 60);

    return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
  };

  const Toast = () =>
    toast && (
      <div className={`toast toast-${toast.type}`}>
        <div className="toast-icon">
          {toast.type === "success" ? "✓" : "!"}
        </div>

        <div>
          <strong>{toast.type === "success" ? "Success" : "Error"}</strong>
          <p>{toast.message}</p>
        </div>
      </div>
    );

  if (!token) {
    return (
      <div className="auth-page">
        <Toast />

        <div className="auth-card">
          <div className="auth-logo">AI</div>

          <h1>Welcome to DocuMind</h1>
          <p>Login or create an account to use your AI document assistant.</p>

          <div className="auth-tabs">
            <button
              className={authMode === "login" ? "active" : ""}
              onClick={() => setAuthMode("login")}
            >
              Login
            </button>

            <button
              className={authMode === "register" ? "active" : ""}
              onClick={() => setAuthMode("register")}
            >
              Register
            </button>
          </div>

          {authMode === "register" && (
            <input
              type="text"
              placeholder="Full name"
              value={authForm.name}
              onChange={(e) =>
                setAuthForm({ ...authForm, name: e.target.value })
              }
            />
          )}

          <input
            type="email"
            placeholder="Email address"
            value={authForm.email}
            onChange={(e) =>
              setAuthForm({ ...authForm, email: e.target.value })
            }
          />

          <input
            type="password"
            placeholder="Password"
            value={authForm.password}
            onChange={(e) =>
              setAuthForm({ ...authForm, password: e.target.value })
            }
          />

          <button className="primary-btn" onClick={handleAuth} disabled={loading}>
            {loading
              ? "Please wait..."
              : authMode === "login"
              ? "Login"
              : "Create Account"}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="app">
      <Toast />

      <header className="hero">
        <div className="hero-overlay"></div>

        <nav className="top-nav">
          <div className="brand">
            <div className="brand-logo">AI</div>
            <div>
              <h2>DocuMind</h2>
              <span>Document & Multimedia Intelligence</span>
            </div>
          </div>

          <div className="nav-pills">
            <span>PDF</span>
            <span>Audio</span>
            <span>Video</span>
            <span>Q&A</span>
            <button className="logout-btn" onClick={logout}>
              Logout
            </button>
          </div>
        </nav>

        <div className="hero-content">
          <div className="hero-badge">⚡ AI Powered Q&A Platform</div>

          <h1>
            Chat With Your <span>Documents, Audio & Videos</span>
          </h1>

          <p>
            Upload PDF, audio, or video files and let AI summarize, answer
            questions, extract timestamps, and play the exact relevant moment.
          </p>

          <div className="hero-actions">
            <button
              className="hero-btn primary-hero"
              onClick={() =>
                document.querySelector('input[type="file"]')?.click()
              }
            >
              Upload Your File
            </button>

            <button
              className="hero-btn secondary-hero"
              onClick={() => window.scrollTo({ top: 420, behavior: "smooth" })}
            >
              Explore Dashboard
            </button>
          </div>

          <div className="hero-stats">
            <div>
              <strong>3+</strong>
              <span>File Types</span>
            </div>
            <div>
              <strong>AI</strong>
              <span>Q&A Chatbot</span>
            </div>
            <div>
              <strong>⏱</strong>
              <span>Timestamp Search</span>
            </div>
          </div>
        </div>
      </header>

      <main className="dashboard">
        <div className="left-column">
          <section className="card upload-card">
            <div className="card-header">
              <h2>Upload File</h2>
              <span className="chip chip-blue">PDF / AUDIO / VIDEO</span>
            </div>

            <div className="upload-box">
              <p className="upload-title">Choose a file to upload</p>
              <p className="upload-subtitle">
                Supported: .pdf, .mp3, .wav, .m4a, .mp4, .mov, .mkv
              </p>

              <input
                type="file"
                accept=".pdf,.mp3,.wav,.m4a,.mp4,.mov,.mkv"
                onChange={(e) => setFile(e.target.files[0])}
              />

              {file && (
                <div className="selected-file">
                  <span>Selected:</span> {file.name}
                </div>
              )}

              <button
                className="primary-btn"
                onClick={uploadFile}
                disabled={loading}
              >
                {loading ? "Processing..." : "Upload File"}
              </button>
            </div>
          </section>

          <section className="card">
            <div className="card-header">
              <h2>Uploaded Files</h2>
              <span className="chip">{documents.length} files</span>
            </div>

            {documents.length === 0 ? (
              <div className="empty-state">
                <p>No files uploaded yet.</p>
              </div>
            ) : (
              <div className="doc-list">
                {documents.map((doc) => (
                  <button
                    key={doc.id}
                    className={`doc-item ${
                      selectedDoc?.id === doc.id ? "active" : ""
                    }`}
                    onClick={() => selectDocument(doc)}
                  >
                    <div className="doc-top">
                      <span className="doc-icon">
                        {getFileIcon(doc.fileType)}
                      </span>

                      <div className="doc-info">
                        <div className="doc-name">{doc.originalFileName}</div>
                        <div className="doc-meta">
                          ID: {doc.id} • Type: {doc.fileType}
                        </div>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </section>
        </div>

        <div className="right-column">
          {selectedDoc ? (
            <section className="card selected-card">
              <div className="card-header">
                <h2>Selected File</h2>
                <span className="chip chip-green">Ready</span>
              </div>

              <div className="selected-grid">
                <div className="info-box">
                  <span className="label">Name</span>
                  <span className="value">{selectedDoc.originalFileName}</span>
                </div>

                <div className="info-box">
                  <span className="label">Type</span>
                  <span className="value">{selectedDoc.fileType}</span>
                </div>

                <div className="info-box">
                  <span className="label">Document ID</span>
                  <span className="value">{selectedDoc.id}</span>
                </div>

                <div className="info-box">
                  <span className="label">Media Support</span>
                  <span className="value">{isMedia ? "Yes" : "No"}</span>
                </div>
              </div>

              {isMedia && (
                <div className="media-section">
                  <h3>Media Preview</h3>

                  {!mediaUrl && <p>Loading media...</p>}

                  {isAudio && mediaUrl && (
                    <audio ref={mediaRef} controls src={mediaUrl} />
                  )}

                  {isVideo && mediaUrl && (
                    <video ref={mediaRef} controls src={mediaUrl} />
                  )}
                </div>
              )}
            </section>
          ) : (
            <section className="card empty-big">
              <h2>No File Selected</h2>
              <p>Please upload or select a file from the left side.</p>
            </section>
          )}

          <section className="card">
            <div className="card-header">
              <h2>Ask AI</h2>
              <span className="chip chip-purple">Chat Assistant</span>
            </div>

            <textarea
              placeholder="Ask something about the selected file..."
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
            />

            <div className="action-row chat-actions">
              <button
                className="primary-btn"
                onClick={askQuestion}
                disabled={loading}
              >
                {loading ? "Thinking..." : "Ask Question"}
              </button>

              <button
                className="secondary-btn stream-btn"
                onClick={askQuestionStreaming}
                disabled={loading}
              >
                {loading ? "Streaming..." : "Ask with Streaming"}
              </button>
            </div>

            {answer && (
              <div className="result-box answer-box">
                <div className="result-header">
                  <h3>AI Answer</h3>

                  {isMedia && hasTimestamp && (
                    <span className="time-pill">⏱ {formatTime(timestamp)}</span>
                  )}
                </div>

                <p>{answer}</p>

                {isMedia && hasTimestamp && (
                  <div className="timestamp-box">
                    <p>
                      <strong>Timestamp:</strong>{" "}
                      {Number(timestamp).toFixed(2)} seconds
                    </p>

                    <button
                      className="secondary-btn"
                      onClick={playFromTimestamp}
                    >
                      ▶ Play From Timestamp
                    </button>
                  </div>
                )}

                {isMedia && !hasTimestamp && (
                  <div className="timestamp-box muted-box">
                    <p>No timestamp found for this answer.</p>
                  </div>
                )}

                {!isMedia && (
                  <div className="timestamp-box muted-box">
                    <p>Timestamp is available only for audio/video files.</p>
                  </div>
                )}
              </div>
            )}
          </section>

          <section className="card">
            <div className="card-header">
              <h2>AI Summary</h2>
              <span className="chip chip-orange">Quick Overview</span>
            </div>

            <button
              className="primary-btn"
              onClick={getSummary}
              disabled={loading}
            >
              {loading ? "Generating..." : "Generate Summary"}
            </button>

            {summary && (
              <div className="result-box summary-box">
                <h3>Summary Result</h3>
                <p>{summary}</p>
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}

export default App;