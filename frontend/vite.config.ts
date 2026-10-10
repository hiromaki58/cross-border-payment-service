/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
    plugins: [react()],
    server: {
        port: 3000,
        // Local development: forward API calls to the Spring Boot backend (same-origin from the browser)
        proxy: {
            "/api": "http://localhost:8080",
        },
    },
    build: {
        // Keep the same output directory as Create React App
        outDir: "build",
    },
    test: {
        environment: "jsdom",
        globals: true,
        setupFiles: "./src/setupTests.ts",
    },
});
