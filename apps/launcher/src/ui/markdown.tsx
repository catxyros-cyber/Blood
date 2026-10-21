/**
 * Renders Modrinth project bodies (Markdown + HTML) and Mojang patch notes
 * (HTML) safely. Everything is passed through an allowlist sanitizer: no
 * scripts, no event handlers, no iframes, no styles, only https/relative links
 * and images. Links open in the system browser.
 */

import MarkdownIt from "markdown-it";
import { useMemo, type MouseEvent } from "react";
import { native } from "../services/native";

const md = new MarkdownIt({ html: true, linkify: true, breaks: false });

const ALLOWED_TAGS = new Set([
  "a", "b", "blockquote", "br", "code", "del", "details", "div", "em", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "i", "img",
  "kbd", "li", "ol", "p", "pre", "s", "small", "span", "strong", "sub", "summary", "sup", "table", "tbody", "td", "th",
  "thead", "tr", "u", "ul", "center", "font",
]);
const ALLOWED_ATTRS: Record<string, Set<string>> = {
  a: new Set(["href", "title"]),
  img: new Set(["src", "alt", "title", "width", "height"]),
  td: new Set(["colspan", "rowspan", "align"]),
  th: new Set(["colspan", "rowspan", "align"]),
  details: new Set(["open"]),
};

function safeUrl(url: string, base?: string): string | null {
  try {
    const u = new URL(url, base ?? "https://modrinth.com/");
    return u.protocol === "https:" ? u.toString() : null;
  } catch {
    return null;
  }
}

export function sanitizeHtml(html: string, base?: string): string {
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, "text/html");
  const root = doc.body.firstElementChild;
  if (!root) return "";
  const walk = (el: Element) => {
    for (const child of [...el.children]) {
      const tag = child.tagName.toLowerCase();
      if (!ALLOWED_TAGS.has(tag)) {
        // Drop dangerous containers entirely; unwrap everything else.
        if (["script", "style", "iframe", "object", "embed", "form", "input", "button", "textarea", "select", "link", "meta", "svg", "math", "video", "audio"].includes(tag)) {
          child.remove();
          continue;
        }
        walk(child);
        child.replaceWith(...child.childNodes);
        continue;
      }
      const allowed = ALLOWED_ATTRS[tag];
      for (const attr of [...child.attributes]) {
        if (!allowed?.has(attr.name)) child.removeAttribute(attr.name);
      }
      if (tag === "a") {
        const href = child.getAttribute("href");
        const safe = href ? safeUrl(href, base) : null;
        if (safe) child.setAttribute("data-href", safe);
        child.removeAttribute("href");
      }
      if (tag === "img") {
        const src = child.getAttribute("src");
        const safe = src ? safeUrl(src, base) : null;
        if (safe) {
          child.setAttribute("src", safe);
          child.setAttribute("loading", "lazy");
          child.setAttribute("referrerpolicy", "no-referrer");
        } else child.remove();
      }
      walk(child);
    }
  };
  walk(root);
  return root.innerHTML;
}

function onClick(e: MouseEvent) {
  const a = (e.target as HTMLElement).closest("a");
  if (!a) return;
  e.preventDefault();
  const href = a.getAttribute("data-href");
  if (href) void native.openUrl(href);
}

export function Markdown({ source, html, base }: { source?: string; html?: string; base?: string }) {
  const rendered = useMemo(() => sanitizeHtml(html ?? md.render(source ?? ""), base), [source, html, base]);
  return <div className="prose-bloom" onClick={onClick} dangerouslySetInnerHTML={{ __html: rendered }} />;
}
