import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// BASE is /monitoring-hub/ on GitHub Pages and / everywhere else.
export default defineConfig({
  base: process.env.BASE || "/",
  plugins: [react()],
  build: { chunkSizeWarningLimit: 900 },
});
