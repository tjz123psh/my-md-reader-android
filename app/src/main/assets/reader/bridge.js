// === MD Reader — Bridge: JS ↔ Android WebView ===
(function () {
  "use strict";

  // ---- Markdown Render ----

  function renderMarkdown(mdText) {
    if (!mdText) return;
    try {
      const md = markdownit("commonmark", {
        html: false,
        linkify: true,
        typographer: true,
        breaks: true,
      }).enable("table");

      // Obsidian image syntax: ![[image.png|width]] → ![alt](path)
      const obsidianRe = /!\[\[([^\]\n]+)\]\]/g;
      mdText = mdText.replace(obsidianRe, (match, raw) => {
        const parts = raw.split("|");
        const name = parts[0].trim();
        return `![${name}](${encodeURI(name)})`;
      });

      // Auto-generate heading IDs so getHeadings() and scrollToHeading() work
      md.renderer.rules.heading_open = function (tokens, idx, options, env, self) {
        const token = tokens[idx];
        const next = tokens[idx + 1];
        const text = next ? next.content : "";
        const id = text
          .toLowerCase()
          .replace(/[^\w\u4e00-\u9fff]+/g, "-")
          .replace(/(^-|-$)/g, "")
          .replace(/^(\d)/, "h-$1")
          || "h" + idx;
        token.attrs = token.attrs || [];
        // Replace any existing id attribute
        const existing = token.attrs.findIndex((a) => a[0] === "id");
        if (existing >= 0) token.attrs[existing][1] = id;
        else token.attrs.push(["id", id]);
        return self.renderToken(tokens, idx, options, env, self);
      };

      const result = md.render(mdText);
      document.getElementById("content").innerHTML = result;

      // Apply highlight.js
      document.querySelectorAll("pre code").forEach((block) => {
        hljs.highlightElement(block);
      });
    } catch (e) {
      document.getElementById("content").innerHTML =
        '<pre class="source-fallback">' + escapeHtml(mdText) + "</pre>";
    }
  }

  function escapeHtml(text) {
    const div = document.createElement("div");
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
  }

  // Auto-render if content was set before script loaded
  if (typeof MD_READER_CONTENT !== "undefined" && MD_READER_CONTENT) {
    renderMarkdown(MD_READER_CONTENT);
  }

  // ---- Helpers ----

  const mappedBlock = (node) => {
    if (!node) return null;
    const el = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
    return el ? el.closest("[data-source-start]") : null;
  };

  const headingFor = (el) => {
    let current = el;
    while (current) {
      let c = current;
      while (c) {
        if (/^H[1-6]$/.test(c.tagName || "")) {
          return { id: c.id || "", title: c.textContent.trim() };
        }
        c = c.previousElementSibling;
      }
      current = current.parentElement;
    }
    return null;
  };

  const clearAnchors = () => {
    document.querySelectorAll('[data-selection-anchor="true"]').forEach((n) =>
      n.removeAttribute("data-selection-anchor")
    );
  };

  // ---- Selection Reporting ----

  const reportSelection = () => {
    clearAnchors();
    const sel = window.getSelection();
    const text = sel ? sel.toString().trim() : "";
    if (!sel || sel.rangeCount === 0 || !text) {
      try { MdReaderAndroid.onSelectionChanged(""); } catch (_) {}
      return;
    }
    const range = sel.getRangeAt(0);
    const first = mappedBlock(range.startContainer);
    const last = mappedBlock(range.endContainer) || first;
    if (!first) return;
    first.setAttribute("data-selection-anchor", "true");
    const startLine = parseInt(first.dataset.sourceStart || "0", 10) || 0;
    const endLine = parseInt(last.dataset.sourceEnd || first.dataset.sourceEnd || "0", 10) || 0;
    try {
      MdReaderAndroid.onSelectionChanged(JSON.stringify({
        text: text.slice(0, 12000),
        startLine: startLine,
        endLine: endLine,
        heading: headingFor(first),
      }));
    } catch (_) {}
  };

  let selTimer = 0;
  document.addEventListener("selectionchange", () => {
    clearTimeout(selTimer);
    selTimer = setTimeout(reportSelection, 80);
  });

  // ---- Active Heading Tracking ----

  let headings = [];
  let activeHeadingId = null;
  let headingDelay = 0;
  let scrollFrame = 0;

  function refreshHeadings() {
    headings = Array.from(
      document.querySelectorAll("h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]")
    );
    activeHeadingId = null;
  }

  const reportActiveHeading = () => {
    scrollFrame = 0;
    if (headings.length === 0) {
      refreshHeadings();
      if (headings.length === 0) return;
    }
    const probe = Math.min(160, Math.max(64, window.innerHeight * 0.18));
    let active = headings[0];
    const atEnd =
      window.scrollY + window.innerHeight >=
      document.documentElement.scrollHeight - 2;
    if (atEnd && headings.length) {
      active = headings[headings.length - 1];
    } else {
      let low = 0, high = headings.length - 1;
      while (low <= high) {
        const mid = Math.floor((low + high) / 2);
        if (headings[mid].getBoundingClientRect().top <= probe) {
          active = headings[mid];
          low = mid + 1;
        } else {
          high = mid - 1;
        }
      }
    }
    const id = active?.id || "";
    if (id === activeHeadingId) return;
    activeHeadingId = id;
    try {
      MdReaderAndroid.onActiveHeadingChanged(JSON.stringify({ id: id }));
    } catch (_) {}
  };

  const scheduleActiveHeading = () => {
    if (headingDelay || scrollFrame) return;
    headingDelay = setTimeout(() => {
      headingDelay = 0;
      scrollFrame = requestAnimationFrame(reportActiveHeading);
    }, 72);
  };

  window.addEventListener("scroll", scheduleActiveHeading, { passive: true });
  window.addEventListener("resize", scheduleActiveHeading, { passive: true });

  // ---- Zoom ----

  const zoomBounds = (p) => Math.max(75, Math.min(200, Number(p) || 100));

  const currentZoom = () => {
    const target = document.body || document.documentElement;
    const v = parseFloat(
      window.getComputedStyle(target).getPropertyValue("--reader-zoom")
    );
    return zoomBounds(Math.round((Number.isFinite(v) ? v : 1) * 100));
  };

  const setZoom = (percent) => {
    const bounded = zoomBounds(percent);
    const target = document.body || document.documentElement;
    target.style.setProperty("--reader-zoom", String(bounded / 100));
    scheduleActiveHeading();
    return bounded;
  };

  // Pinch zoom
  let lastPinchDist = 0;
  let pinchZoomActive = false;

  window.addEventListener("touchstart", (e) => {
    if (e.touches.length === 2) {
      lastPinchDist = Math.hypot(
        e.touches[0].clientX - e.touches[1].clientX,
        e.touches[0].clientY - e.touches[1].clientY
      );
      pinchZoomActive = true;
    }
  }, { passive: true });

  window.addEventListener("touchmove", (e) => {
    if (!pinchZoomActive || e.touches.length < 2) return;
    const dist = Math.hypot(
      e.touches[0].clientX - e.touches[1].clientX,
      e.touches[0].clientY - e.touches[1].clientY
    );
    const delta = dist - lastPinchDist;
    lastPinchDist = dist;
    if (Math.abs(delta) < 4) return;
    const dir = delta > 0 ? 1 : -1;
    const cur = currentZoom();
    const next = zoomBounds(cur + dir * 5);
    if (next !== cur) {
      setZoom(next);
      try { MdReaderAndroid.onZoomChanged(JSON.stringify({ percent: next })); } catch (_) {}
    }
  }, { passive: true });

  window.addEventListener("touchend", () => {
    pinchZoomActive = false;
    lastPinchDist = 0;
  });

  // ---- Public API ----

  const motionBehavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ? "auto" : "smooth";

  window.mdReader = {
    loadContent(mdText) {
      renderMarkdown(mdText);
      setTimeout(refreshHeadings, 50);
      reportActiveHeading();
    },

    setZoom,
    currentZoom,

    setTheme(theme) {
      document.body.setAttribute("data-color-scheme", theme);
      document.querySelectorAll("pre code").forEach((block) => {
        hljs.highlightElement(block);
      });
    },

    scrollToHeading(id) {
      const target = document.getElementById(id);
      if (target) target.scrollIntoView({ block: "start", behavior: motionBehavior });
    },

    scrollToSource(line) {
      const nodes = document.querySelectorAll("[data-source-start]");
      const target = Array.from(nodes).find((n) => {
        const s = parseInt(n.dataset.sourceStart || "0", 10);
        const e = parseInt(n.dataset.sourceEnd || "0", 10);
        return s <= line && line <= e;
      });
      if (target) target.scrollIntoView({ block: "center", behavior: motionBehavior });
    },

    clearSelection() {
      window.getSelection()?.removeAllRanges();
      clearAnchors();
      reportSelection();
    },

    search(query) {
      if (!query) { window.getSelection()?.removeAllRanges(); return; }
      window.find(query, false, false, true, false, true, false);
    },

    clearSearch() {
      window.getSelection()?.removeAllRanges();
    },

    getHeadings() {
      return Array.from(
        document.querySelectorAll("h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]")
      ).map((h) => ({
        id: h.id,
        title: h.textContent.trim(),
        level: parseInt(h.tagName[1], 10),
      }));
    },
  };

  // Signal ready
  try { MdReaderAndroid.onDocumentLoaded(""); } catch (_) {}
})();
