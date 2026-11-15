// Adapted from the Astryx AI chat conversation template.
// Copyright (c) Meta Platforms, Inc. and affiliates.
import { useMemo, useRef, useState } from 'react'
import { Theme } from '@astryxdesign/core/theme'
import { neutralTheme } from '@astryxdesign/theme-neutral/built'
import { MoonIcon, SunIcon, ShoppingCartIcon, XMarkIcon } from '@heroicons/react/24/outline'
import { useChat } from '@ai-sdk/react'
import { DefaultChatTransport, type UIMessage } from 'ai'
import { VStack, Layout, LayoutContent } from '@astryxdesign/core/Layout'
import { ChatLayout, ChatMessageList, ChatMessage, ChatMessageBubble,
  ChatToolCalls, ChatComposer, ChatComposerInput, ChatSystemMessage } from '@astryxdesign/core/Chat'
import { Avatar } from '@astryxdesign/core/Avatar'
import { Markdown } from '@astryxdesign/core/Markdown'
import { Button } from '@astryxdesign/core/Button'
import { VisuallyHidden } from '@astryxdesign/core/VisuallyHidden'
import { ProductList, OrderReview, OrderStatus, request, errorText, type Product, type Receipt } from './Shop'
import { fetchChat } from './fetchChat.mjs'

const toolLabels: Record<string, string> = {
  listOrderIds: 'Consultar tus pedidos',
  getCart: 'Consultar tu carrito',
  openCart: 'Abrir tu carrito',
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

function toolResult(output: unknown) {
  if (typeof output !== 'string') return output
  try { return JSON.parse(output) } catch { return null }
}

const orderPrefix = 'shop-order:'
const trackingIdPattern = /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i

function rememberOrder(id: string) {
  if (!trackingIdPattern.test(id)) return
  const key = orderPrefix + id
  if (!localStorage.getItem(key)) localStorage.setItem(key, new Date().toISOString())
}

function savedOrderIds() {
  return Object.keys(localStorage)
    .filter(key => key.startsWith(orderPrefix) && trackingIdPattern.test(key.slice(orderPrefix.length)))
    .sort((a, b) => (localStorage.getItem(b) ?? '').localeCompare(localStorage.getItem(a) ?? ''))
    .map(key => key.slice(orderPrefix.length))
}

type Cart = { product: Product; units: number }[]

function Message({ message, cart, add, busy }: { message: UIMessage; cart: Cart; add: (product: Product) => void; busy: boolean }) {
  const assistant = message.role === 'assistant'
  return (
    <ChatMessage sender={assistant ? 'assistant' : 'user'} avatar={assistant && <Avatar name="Asistente" size="md" />}>
      {message.parts.map((part, index) => {
        if (part.type === 'text' && part.text) return (
          <ChatMessageBubble key={index} variant={assistant ? 'ghost' : undefined}>
            {assistant ? <Markdown density="compact">{part.text}</Markdown> : part.text}
          </ChatMessageBubble>
        )
        if (part.type === 'data-receipt') return <div key={index} className="market"><OrderStatus initial={part.data as Receipt} /></div>
        if (part.type === 'dynamic-tool' && part.state === 'output-available') {
          const result = toolResult(part.output)
          if (['searchInventory', 'listAvailableProducts'].includes(part.toolName) && Array.isArray(result)) {
            return <div className="market showcase" key={part.toolCallId}><ProductList products={result} cart={cart} add={add} busy={busy} /></div>
          }
          if (['placeOrder', 'trackOrder'].includes(part.toolName) && result?.trackingId && Array.isArray(result.items)) {
            return <div className="market" key={part.toolCallId}><OrderStatus initial={result} /></div>
          }
        }
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

function Conversation({ id, reset, dark, toggleTheme }: { id: string; reset: () => void; dark: boolean; toggleTheme: () => void }) {
  const [draft, setDraft] = useState('')
  const transport = useMemo(() => new DefaultChatTransport({
    api: '/api/chat/stream',
    fetch: fetchChat,
    prepareSendMessagesRequest: ({ messages }) => ({
      body: { conversationId: id, browserCartTools: true, message: messages[messages.length - 1].parts
        .filter(part => part.type === 'text').map(part => part.text).join('') },
    }),
  }), [id])
  const [cart, setCart] = useState<Cart>([])
  const [submitting, setSubmitting] = useState(false)
  const [checkoutError, setCheckoutError] = useState('')
  const [historyError, setHistoryError] = useState('')
  function saveReceiptId(id: string) {
    try { rememberOrder(id) }
    catch { setHistoryError(`No se pudo guardar el pedido en este navegador. Conserva el código ${id}.`) }
  }
  const drawer = useRef<HTMLDialogElement>(null)
  const { messages, setMessages, sendMessage, stop, status, error } = useChat({
    id, transport,
    onData(part) {
      if (part.type === 'data-order-placed') {
        const receipt = toolResult(part.data)
        if (receipt?.trackingId) saveReceiptId(receipt.trackingId)
        return
      }
      if (part.type !== 'data-browser-tool') return
      const { callId, tool } = part.data as { callId: string; tool: string }
      if (tool !== 'getCart' && tool !== 'openCart' && tool !== 'listOrderIds') return
      if (tool === 'openCart' && !drawer.current?.open) drawer.current?.showModal()
      let result
      try {
        result = tool === 'listOrderIds'
          ? { orderIds: savedOrderIds() }
          : { items: cart.map(({ product, units }) => ({ product: product.name, quantity: units })) }
      } catch {
        setHistoryError('No se pudo leer el historial de pedidos en este navegador.')
        result = {}
      }
      // The stream stays open while the server waits for this call's result.
      void fetch('/api/chat/browser-results/' + encodeURIComponent(callId), {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(result),
      }).catch(() => { /* The pending tool reports a timeout to the agent. */ })
    },
  })
  const count = cart.reduce((sum, line) => sum + line.units, 0)
  function add(product: Product) {
    setCheckoutError('')
    setCart(lines => {
      if (!lines.some(line => line.product.id === product.id)) return [...lines, { product, units: 1 }]
      return lines.map(line => line.product.id === product.id ? { product, units: Math.min(product.quantity, line.units + 1) } : line)
    })
  }
  async function confirm(address: string) {
    if (!cart.length || submitting || busy) return false
    setSubmitting(true); setCheckoutError('')
    try {
      const receipt = await request<Receipt>('orders', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ address, items: cart.map(line => ({ product: line.product.name, quantity: line.units })) }) })
      saveReceiptId(receipt.trackingId)
      setMessages(previous => [...previous, { id: crypto.randomUUID(), role: 'assistant', parts: [{ type: 'data-receipt', data: receipt }] }])
      drawer.current?.close()
      setCart([])
      return true
    } catch (err) { setCheckoutError(errorText(err)); return false }
    finally { setSubmitting(false) }
  }
  const busy = status === 'submitted' || status === 'streaming'
  return (
    <VStack style={{ height: '100dvh', width: '100%' }}>
      <Layout height="fill" content={
        <LayoutContent role="main" padding={0}>
          <VisuallyHidden as="h1">Asistente de tareas y pedidos</VisuallyHidden>
          <VStack height="100%">
            <header className="shop-toolbar">
              <button className="theme-toggle" type="button" aria-label="Tema oscuro" aria-pressed={dark}
                title={dark ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'} onClick={toggleTheme}>
                {dark ? <SunIcon /> : <MoonIcon />}
              </button>
              <button className="cart-trigger" aria-label={`Abrir carrito, ${count} unidades`} aria-haspopup="dialog" onClick={() => drawer.current?.showModal()}>
                <ShoppingCartIcon /><span className="cart-count">{count}</span>
              </button>
            </header>
            {historyError && <p role="alert">{historyError}</p>}
            <dialog ref={drawer} className="cart-drawer" aria-labelledby="cart-title"
              onClick={event => { if (event.target === drawer.current) drawer.current.close() }}>
              <div className="drawer-content">
                <header><h2 id="cart-title">Tu pedido</h2><button className="close-cart" aria-label="Cerrar carrito" onClick={() => drawer.current?.close()}><XMarkIcon /></button></header>
                <OrderReview cart={cart} busy={busy || submitting} confirm={confirm}
                  update={(id, units) => {
                    setCheckoutError('')
                    setCart(lines => lines.map(line => line.product.id === id ? { ...line, units } : line).filter(line => line.units > 0))
                  }} />
                {checkoutError && <p role="alert">{checkoutError}</p>}
              </div>
            </dialog>
            <ChatLayout density="spacious" style={{ flex: 1, minHeight: 0 }} composer={
              <ChatComposer value={draft} onChange={setDraft} onSubmit={value => {
                const text = value.trim()
                if (!text || submitting || busy || text.length > 4000) return
                setDraft('')
                void sendMessage({ text })
              }} onStop={stop} isStopShown={busy} input={<ChatComposerInput />}
                placeholder="Pregunta por tus tareas o pedidos…"
                status={draft.trim().length > 4000 ? { type: 'error', message: 'Escribe un mensaje de máximo 4000 caracteres.' }
                  : error ? { type: 'error', message: 'El asistente no pudo responder. Intenta de nuevo.' } : undefined}
                footerActions={messages.length > 0 && <Button isDisabled={submitting} label="Nueva conversación" variant="ghost" size="sm"
                  onClick={() => { void stop(); reset() }} />} />
            }>
              <ChatMessageList>
                <ChatSystemMessage variant="divider">Hoy</ChatSystemMessage>
                {messages.length === 0 && <ChatMessage sender="assistant" avatar={<Avatar name="Asistente" size="md" />}>
                  <ChatMessageBubble variant="ghost">Puedo ayudarte con tus tareas, hacer un pedido de supermercado a domicilio o consultar el estado de tus productos. ¿Qué necesitas?</ChatMessageBubble>
                  <Button label="Ver productos disponibles" size="sm" onClick={() => void sendMessage({ text: "¿Qué productos hay disponibles?" })} />
                </ChatMessage>}
                {messages.map(message => <Message key={message.id} message={message} cart={cart} add={add} busy={busy || submitting} />)}
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
  const [dark, setDark] = useState(() => {
    const saved = localStorage.getItem('shop-theme')
    return saved ? saved === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches
  })
  function toggleTheme() {
    setDark(!dark)
    localStorage.setItem('shop-theme', dark ? 'light' : 'dark')
  }
  return <Theme theme={neutralTheme} mode={dark ? 'dark' : 'light'}>
    <Conversation key={id} id={id} reset={() => setId(crypto.randomUUID())} dark={dark} toggleTheme={toggleTheme} />
  </Theme>
}
