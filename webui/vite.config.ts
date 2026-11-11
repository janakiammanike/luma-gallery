import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: '../app/src/main/assets/web',
    emptyOutDir: true,
    target: 'es2019',
    assetsInlineLimit: 4096,
  },
  server: {
    port: 5199,
    strictPort: false,
  },
})
