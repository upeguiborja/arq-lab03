// Adapted from the Astryx AI chat conversation template.
// Copyright (c) Meta Platforms, Inc. and affiliates.
import { useMemo, useState } from 'react'
import { useChat } from '@ai-sdk/react'
import { DefaultChatTransport, type UIMessage } from 'ai'
import { VStack, Layout, LayoutContent } from '@astryxdesign/core/Layout'
import { ChatLayout, ChatMessageList, ChatMessage, ChatMessageBubble,
  ChatToolCalls, ChatComposer, ChatComposerInput, ChatSystemMessage } from '@astryxdesign/core/Chat'
import { Avatar } from '@astryxdesign/core/Avatar'
import { Markdown } from '@astryxdesign/core/Markdown'
import { Button } from '@astryxdesign/core/Button'
import { VisuallyHidden } from '@astryxdesign/core/VisuallyHidden'
import { fetchChat } from './fetchChat.mjs'

function Message({ message }: { message: UIMessage }) {
  const assistant = message.role === 'assistant'
  return (
    <ChatMessage sender={assistant ? 'assistant' : 'user'} avatar={assistant && <Avatar name="Assistant" size="md" />}>
      {message.parts.map((part, index) => {
        if (part.type === 'text' && part.text) return (
          <ChatMessageBubble key={index} variant={assistant ? 'ghost' : undefined}>
            {assistant ? <Markdown density="compact">{part.text}</Markdown> : part.text}
          </ChatMessageBubble>
        )
        if (part.type === 'dynamic-tool') return (
          <ChatToolCalls key={part.toolCallId} calls={[{
            name: part.toolName,
            target: part.input && typeof part.input === 'object' && 'arguments' in part.input
              ? String(part.input.arguments) : undefined,
            status: part.state === 'output-available' ? 'complete' : part.state === 'output-error' ? 'error' : 'running',
            errorMessage: part.state === 'output-error' ? part.errorText : undefined,
          }]} />
        )
        return null
      })}
    </ChatMessage>
  )
}

function Conversation({ id, reset }: { id: string; reset: () => void }) {
  const [draft, setDraft] = useState('')
  const transport = useMemo(() => new DefaultChatTransport({
    api: '/api/chat/stream',
    fetch: fetchChat,
    prepareSendMessagesRequest: ({ messages }) => ({
      body: { conversationId: id, message: messages[messages.length - 1].parts
        .filter(part => part.type === 'text').map(part => part.text).join('') },
    }),
  }), [id])
  const { messages, sendMessage, stop, status, error } = useChat({ id, transport })
  const busy = status === 'submitted' || status === 'streaming'
  return (
    <VStack style={{ height: '100dvh', width: '100%' }}>
      <Layout height="fill" content={
        <LayoutContent role="main" padding={0}>
          <VisuallyHidden as="h1">Task Assistant</VisuallyHidden>
          <VStack height="100%">
            <ChatLayout density="spacious" style={{ flex: 1, minHeight: 0 }} composer={
              <ChatComposer value={draft} onChange={setDraft} onSubmit={value => {
                const text = value.trim()
                if (!text || busy || text.length > 4000) return
                setDraft('')
                void sendMessage({ text })
              }} onStop={stop} isStopShown={busy} input={<ChatComposerInput />}
                placeholder="Ask about your tasks…"
                status={draft.trim().length > 4000 ? { type: 'error', message: 'Use 4,000 characters or fewer.' }
                  : error ? { type: 'error', message: 'The assistant could not reply. Please try again.' } : undefined}
                footerActions={messages.length > 0 && <Button label="New conversation" variant="ghost" size="sm"
                  onClick={() => { void stop(); reset() }} />} />
            }>
              <ChatMessageList>
                <ChatSystemMessage variant="divider">Today</ChatSystemMessage>
                {messages.length === 0 && <ChatMessage sender="assistant" avatar={<Avatar name="Assistant" size="md" />}>
                  <ChatMessageBubble variant="ghost">What would you like to do with your tasks?</ChatMessageBubble>
                </ChatMessage>}
                {messages.map(message => <Message key={message.id} message={message} />)}
                {status === 'submitted' && <ChatSystemMessage>Thinking…</ChatSystemMessage>}
              </ChatMessageList>
            </ChatLayout>
          </VStack>
        </LayoutContent>
      } />
    </VStack>
  )
}

export default function App() {
  const [id, setId] = useState(() => crypto.randomUUID())
  return <Conversation key={id} id={id} reset={() => setId(crypto.randomUUID())} />
}
