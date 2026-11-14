import { defineConfig } from "vite";
import { resolve } from "node:path";

export default defineConfig({
  root: resolve(import.meta.dirname),
  server: { host: "0.0.0.0", port: 5173 },
  build: { outDir: "../dist", emptyOutDir: true }
});
