import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Read VITE_* variables from the repository-root .env shared with the API and
  // Docker Compose. Only VITE_-prefixed values are exposed to the browser bundle.
  envDir: '../..',
  server: {
    port: 5173,
    // The API's CORS allow-list names this exact origin, so fail instead of
    // silently moving to another port.
    strictPort: true,
  },
  test: {
    environment: 'node',
  },
})
