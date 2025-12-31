import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.{ts,tsx}']
  },
  // Avoid loading the project's vite.config.ts which may be ESM-only
  vite: {
    configFile: false
  }
})
