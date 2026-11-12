import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// In sviluppo le chiamate /api vanno al backend Spring su :8080.
// In build il frontend finisce dentro il jar Spring (src/main/resources/static).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    // Niente asset inline (data:) cosi' la Content-Security-Policy resta stretta.
    assetsInlineLimit: 0,
  },
})
