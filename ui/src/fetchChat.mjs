export function fetchChat(input, init = {}) {
  const timeout = AbortSignal.timeout(60_000)
  const signal = init.signal ? AbortSignal.any([init.signal, timeout]) : timeout
  return fetch(input, { ...init, signal })
}
