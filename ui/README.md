# Task Assistant UI

This Vite/React app adapts Astryx's `ai-chat` starter for a text-only task assistant. It uses the Vercel AI SDK `useChat` hook and Astryx chat components.

## Run

Start the Spring application on port 8080, then:

```sh
cd ui
npm install
npm run dev
```

Open the Vite URL shown in the terminal. Vite proxies `/api` to Spring. `npm run build` creates a production bundle in `ui/dist`. Build the UI before running or packaging Spring so Maven includes this same frontend at `/`.

`POST /api/chat/stream` accepts `{ "message": "...", "conversationId": "<uuid>" }` and emits AI SDK UI message SSE frames. The existing `POST /api/chat` JSON endpoint remains available. A new conversation creates a new UUID and clears the local transcript. The server holds the last 20 messages per conversation in memory.

The UI shows streamed assistant text, a thinking indicator while waiting, and tool names, arguments, and completion states. The model's private reasoning is not exposed by the Spring AI/Gemini stream. Chat history is kept in memory for the current page session; refresh starts a new conversation.
