import { useState } from 'react'
import { MagnifyingGlassIcon } from '@heroicons/react/24/outline'
import { Button } from '@astryxdesign/core/Button'
import { Badge } from '@astryxdesign/core/Badge'
import { Card } from '@astryxdesign/core/Card'

export type Product = { id: number; name: string; description: string; quantity: number }
type Line = { product: Product; units: number }
export type Receipt = { trackingId: string; items: { lineNumber: number; product: string; quantity: number; statusLabel: string }[] }

export async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch('/api/shop/' + path, { cache: 'no-store', ...options })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || 'No se pudo completar la solicitud.')
  }
  return response.json()
}

export function errorText(error: unknown) {
  return error instanceof Error && !(error instanceof TypeError) ? error.message : 'No se pudo conectar con el supermercado.'
}

function productImage(name: string) {
  return '/products/' + name.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replaceAll(' ', '-') + '.jpg'
}

const pageSize = 6

export function ProductList({ products, cart, busy, add }: {
  products: Product[]; cart: Line[]; busy: boolean; add: (product: Product) => void
}) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const normalize = (text: string) => text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
  const visible = products.filter(p => normalize(p.name).includes(normalize(query.trim())))
  const pages = Math.ceil(visible.length / pageSize)
  const current = Math.min(page, Math.max(1, pages))
  const start = (current - 1) * pageSize
  return <section><div className="catalog-heading"><h2>Productos disponibles</h2>
    <label className="catalog-search"><MagnifyingGlassIcon aria-hidden="true" /><input aria-label="Buscar productos por nombre" placeholder="Buscar productos…" type="search" value={query} onChange={e => { setQuery(e.target.value); setPage(1) }} /></label></div>
    {!visible.length && <p>No hay productos disponibles para esta búsqueda.</p>}
    <div className="product-grid" role="region" aria-label="Productos del catálogo" tabIndex={0}>{visible.slice(start, start + pageSize).map(p => {
      const units = cart.find(line => line.product.id === p.id)?.units ?? 0
      return <Card key={p.id} className="catalog-card" style={{ backgroundColor: 'var(--shop-surface)', color: 'var(--shop-text)', borderRadius: '1.15rem' }}><div className="product"><h3>{p.name}</h3><p>{p.description}</p>
        <img loading="lazy" alt={p.name} src={productImage(p.name)}
          onError={e => { e.currentTarget.hidden = true }} />
        <small>{p.quantity} disponibles</small>
        <Button size="sm" label={units ? `Agregar otra unidad (seleccionadas: ${units})` : 'Agregar al pedido'}
          aria-label={`Agregar una unidad de ${p.name}`} isDisabled={busy || units >= p.quantity} onClick={() => add(p)} />
      </div></Card>
    })}</div>
    {pages > 1 && <nav className="catalog-pagination" aria-label="Páginas del catálogo">
      <Button label="Anterior" size="sm" variant="ghost" isDisabled={current === 1} onClick={() => setPage(current - 1)} />
      <span aria-live="polite">Página {current} de {pages}</span>
      <Button label="Siguiente" size="sm" variant="ghost" isDisabled={current === pages} onClick={() => setPage(current + 1)} />
    </nav>}
  </section>
}

export function OrderReview({ cart, busy, update, confirm }: {
  cart: Line[]; busy: boolean; update: (id: number, units: number) => void; confirm: (address: string) => Promise<boolean>
}) {
  const [address, setAddress] = useState('')
  return <div className="market drawer-order">{!cart.length ? <p>Agrega productos del catálogo.</p> :
    <form onSubmit={async e => { e.preventDefault(); if (await confirm(address.trim())) setAddress('') }}>
      <ul>{cart.map(({ product, units }) => <li key={product.id}><img src={productImage(product.name)} alt="" />
        <span>{product.name}</span>
        <div className="quantity-control">
          <button type="button" disabled={busy} aria-label={`Quitar una unidad de ${product.name}`} onClick={() => update(product.id, units - 1)}>−</button>
          <output aria-label={`Cantidad de ${product.name}`}>{units}</output>
          <button type="button" disabled={busy || units >= product.quantity} aria-label={`Agregar una unidad de ${product.name}`} onClick={() => update(product.id, units + 1)}>+</button>
        </div></li>)}</ul>
      <label>Dirección de entrega<input autoComplete="street-address" maxLength={255} required disabled={busy}
        value={address} onChange={e => setAddress(e.target.value)} /></label>
      <Button type="submit" label="Confirmar pedido" isLoading={busy} isDisabled={!address.trim() || busy} />
    </form>}</div>
}

export function OrderStatus({ initial }: { initial: Receipt | null }) {
  const [code, setCode] = useState(initial?.trackingId ?? '')
  const [receipt, setReceipt] = useState(initial)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  return <Card><h2>Seguimiento del pedido</h2>
    <form onSubmit={async e => {
      e.preventDefault(); setBusy(true); setError('')
      try { setReceipt(await request<Receipt>('orders/' + encodeURIComponent(code.trim()))) }
      catch (err) { setReceipt(null); setError(errorText(err)) }
      finally { setBusy(false) }
    }}>
      <label>Código de seguimiento<input required value={code} disabled={busy} onChange={e => setCode(e.target.value)}
        pattern="[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}" title="Ingresa el código completo del pedido, incluidos los guiones." /></label>
      <Button label="Consultar estado" type="submit" isDisabled={!code.trim() || busy} isLoading={busy} />
    </form>{error && <p role="alert">{error}</p>}
    {receipt && <div aria-live="polite"><p>Código del pedido: <strong>{receipt.trackingId}</strong></p>
      <ul>{receipt.items.map(line => <li key={line.lineNumber}>{line.product} × {line.quantity}
        <Badge label={line.statusLabel} /></li>)}</ul></div>}
  </Card>
}

