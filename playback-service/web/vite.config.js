import { defineConfig } from "vite";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  root,
  server: { host: "0.0.0.0", port: 5173 },
  build: { outDir: "../dist", emptyOutDir: true }
});
