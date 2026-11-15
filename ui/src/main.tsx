import React from 'react'
import ReactDOM from 'react-dom/client'
import { InternationalizationProvider } from '@astryxdesign/core/i18n'
import spanish from '@astryxdesign/core/locales/es-ES.generated.js'
import App from './App'
import './style.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
      <InternationalizationProvider locale="es-CO" messages={{ 'es-CO': spanish }}
        overrides={{ 'es-CO': {
          '@astryx.chatMessage.messageFrom': '{sender, select, assistant {Mensaje del asistente} user {Tu mensaje} other {Mensaje del sistema}}',
        } }}>
        <App />
      </InternationalizationProvider>
  </React.StrictMode>,
)
