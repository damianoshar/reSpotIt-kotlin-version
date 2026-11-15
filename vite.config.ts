import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    sourcemap: false,
    // Android 7 devices ship Chrome 64, which cannot parse `?.` or `??`.
    // Keep any web shell this repo serves parseable on that WebView.
    target: 'es2017',
  },
  server: {
    port: 3000,
    strictPort: true,
  },
  // Ensure Capacitor assets resolve correctly
  base: './',
  optimizeDeps: {
    include: ['papaparse'],
  },
});
