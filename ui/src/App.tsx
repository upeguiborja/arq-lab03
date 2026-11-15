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

const toolLabels: Record<string, string> = {
  listAvailableProducts: 'Ver productos disponibles',
  searchInventory: 'Consultar inventario',
  restockProduct: 'Registrar entrada de inventario',
  listTasks: 'Listar tareas',
  countTasks: 'Contar tareas',
  listPending: 'Consultar tareas pendientes',
  listCompletedTasks: 'Consultar tareas completadas',
  searchTasks: 'Buscar tareas',
  createTask: 'Crear tarea',
  markCompleted: 'Marcar tarea como completada',
  updateTask: 'Actualizar tarea',
  deleteTask: 'Eliminar tarea',
  placeOrder: 'Registrar pedido',
  trackOrder: 'Consultar estado del pedido',
  updateItemStatus: 'Actualizar estado de entrega',
}

const argumentLabels: Record<string, string> = {
  productId: 'Identificador del producto', id: 'Identificador', fragment: 'Texto de búsqueda', title: 'Título', date: 'Fecha',
  address: 'Dirección', items: 'Productos', product: 'Producto', quantity: 'Cantidad',
  trackingId: 'Código de seguimiento', lineNumber: 'Número de línea', status: 'Estado',
}

function toolDetails(input: unknown): string | undefined {
  if (!input || typeof input !== 'object' || !('arguments' in input) || typeof input.arguments !== 'string') return
  try {
    const args = JSON.parse(input.arguments)
    return Object.entries(args)
      .filter(([key, value]) => key in argumentLabels && value != null)
      .map(([key, value]) => {
        if (key === 'items' && Array.isArray(value)) {
          return value.map(item => `${item.product} × ${item.quantity}`).join(', ')
        }
        return `${argumentLabels[key]}: ${value}`
      })
      .join(' · ')
  } catch {
    return undefined
  }
}

function Message({ message }: { message: UIMessage }) {
  const assistant = message.role === 'assistant'
  return (
    <ChatMessage sender={assistant ? 'assistant' : 'user'} avatar={assistant && <Avatar name="Asistente" size="md" />}>
      {message.parts.map((part, index) => {
        if (part.type === 'text' && part.text) return (
          <ChatMessageBubble key={index} variant={assistant ? 'ghost' : undefined}>
            {assistant ? <Markdown density="compact">{part.text}</Markdown> : part.text}
          </ChatMessageBubble>
        )
        if (part.type === 'dynamic-tool') return (
          <ChatToolCalls key={part.toolCallId} calls={[{
            name: toolLabels[part.toolName] ?? "Operación",
            target: toolDetails(part.input),
            status: part.state === 'output-available' ? 'complete' : part.state === 'output-error' ? 'error' : 'running',
            errorMessage: part.state === 'output-error' ? 'No se pudo completar la operación.' : undefined,
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
          <VisuallyHidden as="h1">Asistente de tareas y pedidos</VisuallyHidden>
          <VStack height="100%">
            <ChatLayout density="spacious" style={{ flex: 1, minHeight: 0 }} composer={
              <ChatComposer value={draft} onChange={setDraft} onSubmit={value => {
                const text = value.trim()
                if (!text || busy || text.length > 4000) return
                setDraft('')
                void sendMessage({ text })
              }} onStop={stop} isStopShown={busy} input={<ChatComposerInput />}
                placeholder="Pregunta por tus tareas o pedidos…"
                status={draft.trim().length > 4000 ? { type: 'error', message: 'Escribe un mensaje de máximo 4000 caracteres.' }
                  : error ? { type: 'error', message: 'El asistente no pudo responder. Intenta de nuevo.' } : undefined}
                footerActions={messages.length > 0 && <Button label="Nueva conversación" variant="ghost" size="sm"
                  onClick={() => { void stop(); reset() }} />} />
            }>
              <ChatMessageList>
                <ChatSystemMessage variant="divider">Hoy</ChatSystemMessage>
                {messages.length === 0 && <ChatMessage sender="assistant" avatar={<Avatar name="Asistente" size="md" />}>
                  <ChatMessageBubble variant="ghost">Puedo ayudarte con tus tareas, hacer un pedido de supermercado a domicilio o consultar el estado de tus productos. ¿Qué necesitas?</ChatMessageBubble>
                </ChatMessage>}
                {messages.map(message => <Message key={message.id} message={message} />)}
                {status === 'submitted' && <ChatSystemMessage>Pensando…</ChatSystemMessage>}
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
