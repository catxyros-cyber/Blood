import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";
import { App } from "./ui/App";

// Apply the last-used theme before anything renders, so the loading screen matches it.
try {
  const theme = localStorage.getItem("blood.theme");
  if (theme && /^[a-z]+$/.test(theme)) document.documentElement.dataset.theme = theme;
} catch {
  /* storage unavailable */
}

// BLOOD is a desktop app: no browser context menu (except in text fields) and no file drops into the page itself.
window.addEventListener("contextmenu", (e) => {
  const t = e.target as HTMLElement;
  if (!t.closest("input, textarea, .selectable")) e.preventDefault();
});
window.addEventListener("dragover", (e) => e.preventDefault());
window.addEventListener("drop", (e) => e.preventDefault());

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
