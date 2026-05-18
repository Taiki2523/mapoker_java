import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { GoogleOAuthProvider } from '@react-oauth/google'
import './index.css'
import App from './App.tsx'

// Docker: docker-entrypoint.sh が起動時に config.js を生成して window.__CONFIG__ を設定する
// ローカル開発: .env.local の VITE_GOOGLE_CLIENT_ID にフォールバック
const googleClientId =
  (window as unknown as { __CONFIG__?: { googleClientId?: string } }).__CONFIG__?.googleClientId ||
  import.meta.env.VITE_GOOGLE_CLIENT_ID ||
  ''

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <GoogleOAuthProvider clientId={googleClientId}>
        <App />
      </GoogleOAuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
