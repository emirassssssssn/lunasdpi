(() => {
  const docs = window.LUNA_DOCS;
  const sidebar = document.getElementById("sidebar");
  const main = document.getElementById("content");
  const menuBtn = document.getElementById("menuBtn");
  const searchLayer = document.getElementById("searchLayer");
  const searchOpen = document.getElementById("searchOpen");
  const searchInput = document.getElementById("searchInput");
  const searchResults = document.getElementById("searchResults");

  const pages = new Map();
  docs.guides.forEach((g) => pages.set(g.id, { type: "guide", data: g }));
  docs.classes.forEach((c) => pages.set(c.id, { type: "class", data: c }));

  function esc(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  function highlightLua(source) {
    const escaped = esc(source);
    return escaped
      .replace(/(\-\-.*)$/gm, '<span class="cm">$1</span>')
      .replace(/("[^"]*"|'[^']*')/g, '<span class="st">$1</span>')
      .replace(/\b(local|function|return|if|then|else|elseif|end|for|while|do|and|or|not|in|repeat|until)\b/g, '<span class="kw">$1</span>')
      .replace(/\b(nil|true|false)\b/g, '<span class="num">$1</span>')
      .replace(/\b(luna|client|require)\b/g, '<span class="fn">$1</span>');
  }

  function typeHtml(type) {
    if (!type) return "";
    return String(type)
      .split("|")
      .map((part) => {
        const trimmed = part.trim();
        const link = docs.classes.find((c) => c.name === trimmed || c.id === trimmed);
        if (link) return `<a href="#${esc(link.id)}">${esc(trimmed)}</a>`;
        return `<span class="t">${esc(trimmed)}</span>`;
      })
      .join(" | ");
  }

  function badges(item) {
    const out = [];
    if (item.perm) out.push(`<span class="badge perm">${esc(item.perm)}</span>`);
    if (item.static) out.push(`<span class="badge">static</span>`);
    if (item.deprecated) out.push(`<span class="badge warn">deprecated</span>`);
    return out.join(" ");
  }

  function codeBlock(source, lang) {
    const body = lang === "lua" ? highlightLua(source) : esc(source);
    return `<div class="code"><button class="copy-btn" type="button">Copy</button><pre>${body}</pre></div>`;
  }

  function memberId(cls, member) {
    return `${cls.id}.${member.name}`;
  }

  function renderParams(params) {
    if (!params?.length) return "";
    const rows = params
      .map((p) => {
        const name = p.optional ? `${p.name}?` : p.name;
        return `<tr><td><code>${esc(name)}</code></td><td>${typeHtml(p.type)}</td><td>${esc(p.desc || "")}</td></tr>`;
      })
      .join("");
    return `<table class="params"><thead><tr><th>Parameter</th><th>Type</th><th>Description</th></tr></thead><tbody>${rows}</tbody></table>`;
  }

  function renderMember(cls, member) {
    const id = memberId(cls, member);
    const prefix = member.kind === "prop" ? "." : member.static ? "." : ":";
    const heading = member.kind === "prop" ? member.name : (member.sig || `${member.name}()`);
    const returns = member.returns
      ? `<p class="returns"><strong>Returns:</strong> ${typeHtml(member.returns.type)} — ${esc(member.returns.desc || "")}</p>`
      : "";
    const aliases = member.aliases?.length
      ? `<p class="aliases">Also: ${member.aliases.map((a) => `<code>${esc(a)}</code>`).join(", ")}</p>`
      : "";
    const examples = (member.examples || []).map((ex) => codeBlock(ex, "lua")).join("");
    const notes = member.notes ? `<div class="callout">${member.notes}</div>` : "";
    return `
      <article class="member-card" id="${esc(id)}">
        <h3><span class="dot">${prefix}</span>${esc(heading)}</h3>
        <div class="member-meta">${badges(member)}</div>
        <p class="sig">${esc(cls.name)}${prefix}${esc(member.sig || member.name)}</p>
        <p>${esc(member.desc || "")}</p>
        ${aliases}
        ${renderParams(member.params)}
        ${returns}
        ${notes}
        ${examples}
      </article>`;
  }

  function tocFor(cls) {
    const links = [];
    (cls.props || []).forEach((p) => links.push(`<a href="#${esc(memberId(cls, p))}">.${esc(p.name)}</a>`));
    (cls.sections || []).forEach((section) => {
      section.members.forEach((m) => links.push(`<a href="#${esc(memberId(cls, m))}">${m.kind === "prop" ? "." : ":"}${esc(m.name)}</a>`));
    });
    if (!links.length) return "";
    return `<nav class="toc"><h4>On this page</h4>${links.join("")}</nav>`;
  }

  function renderClass(cls) {
    const props = (cls.props || [])
      .map((p) => `<tr id="${esc(memberId(cls, p))}"><td>.${esc(p.name)}</td><td>${typeHtml(p.type)}</td><td>${esc(p.desc || "")} ${badges(p)}</td></tr>`)
      .join("");
    const propTable = props
      ? `<h2 class="section-title">Properties</h2><table class="prop-table"><thead><tr><th>Name</th><th>Type</th><th>Description</th></tr></thead><tbody>${props}</tbody></table>`
      : "";
    const construct = cls.construct
      ? `<div class="construct"><h3>Constructor</h3><p>${esc(cls.construct)}</p>${cls.constructExample ? codeBlock(cls.constructExample, "lua") : ""}</div>`
      : "";
    const sections = (cls.sections || [])
      .map((section) => `<h2 class="section-title">${esc(section.title)}</h2>${section.members.map((m) => renderMember(cls, m)).join("")}`)
      .join("");
    const extendsHtml = cls.extends ? `<span class="ext">extends <a href="#${esc(cls.extends)}">${esc(cls.extends)}</a></span>` : "";
    return `
      <div class="crumbs"><a href="#welcome">Docs</a> / ${esc(cls.kind || "class")} / ${esc(cls.name)}</div>
      <div class="header-row"><span class="badge">${esc(cls.kind || "class")}</span><h1 class="hero-title">${esc(cls.name)}</h1>${extendsHtml}</div>
      <p class="lead">${esc(cls.summary || "")}</p>
      ${cls.notes || ""}
      ${construct}
      ${tocFor(cls)}
      ${propTable}
      ${sections}`;
  }

  function renderGuide(guide) {
    return `
      <div class="crumbs"><a href="#welcome">Docs</a> / Guide / ${esc(guide.title)}</div>
      <h1 class="hero-title">${esc(guide.title)}</h1>
      ${guide.html}`;
  }

  function activeId() {
    const raw = (location.hash || "#welcome").slice(1);
    if (!raw) return "welcome";
    return raw.split(".")[0];
  }

  function memberFromHash() {
    const raw = (location.hash || "").slice(1);
    const dot = raw.indexOf(".");
    return dot > 0 ? raw : "";
  }

  function render() {
    const id = activeId();
    const page = pages.get(id) || pages.get("welcome");
    sidebar.querySelectorAll("a").forEach((a) => {
      a.classList.toggle("active", a.getAttribute("href") === `#${page.data.id}`);
    });
    main.innerHTML = page.type === "class" ? renderClass(page.data) : renderGuide(page.data);
    main.querySelectorAll(".copy-btn").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const text = btn.parentElement.querySelector("pre")?.innerText || "";
        try {
          await navigator.clipboard.writeText(text);
          btn.textContent = "Copied";
          setTimeout(() => { btn.textContent = "Copy"; }, 1200);
        } catch {
          btn.textContent = "Copy failed";
        }
      });
    });
    const member = memberFromHash();
    if (member) {
      const el = document.getElementById(member);
      if (el) el.scrollIntoView({ block: "start" });
    } else {
      main.scrollTop = 0;
      window.scrollTo(0, 0);
    }
    sidebar.classList.remove("open");
    closeSearch();
    document.title = `${page.data.title || page.data.name} | Lunas DPI Lua API`;
  }

  function buildSidebar() {
    const groups = [
      ["Guide", docs.guides.map((g) => ({ href: `#${g.id}`, label: g.title, kind: "" }))],
      ["Classes", docs.classes.filter((c) => (c.kind || "class") === "class").map((c) => ({ href: `#${c.id}`, label: c.name, kind: "C" }))],
      ["Typedefs", docs.classes.filter((c) => c.kind === "typedef").map((c) => ({ href: `#${c.id}`, label: c.name, kind: "T" }))],
    ];
    sidebar.innerHTML = groups
      .filter(([, items]) => items.length)
      .map(([title, items]) => `<h3>${title}</h3>${items.map((item) => `<a href="${item.href}"><span class="kind">${item.kind}</span>${esc(item.label)}</a>`).join("")}`)
      .join("");
  }

  const searchIndex = [];
  docs.guides.forEach((g) => searchIndex.push({ href: `#${g.id}`, title: g.title, hint: "Guide", hay: `${g.title} ${g.id}`.toLowerCase() }));
  docs.classes.forEach((c) => {
    searchIndex.push({ href: `#${c.id}`, title: c.name, hint: c.kind || "Class", hay: `${c.name} ${c.summary || ""}`.toLowerCase() });
    (c.props || []).forEach((p) => searchIndex.push({ href: `#${c.id}.${p.name}`, title: `${c.name}.${p.name}`, hint: "Property", hay: `${c.name} ${p.name} ${p.desc || ""}`.toLowerCase() }));
    (c.sections || []).forEach((section) => {
      section.members.forEach((m) => {
        searchIndex.push({
          href: `#${c.id}.${m.name}`,
          title: `${c.name}${m.kind === "prop" ? "." : ":"}${m.name}`,
          hint: section.title,
          hay: `${c.name} ${m.name} ${(m.aliases || []).join(" ")} ${m.desc || ""} ${m.perm || ""}`.toLowerCase(),
        });
      });
    });
  });

  function runSearch(query) {
    const q = query.trim().toLowerCase();
    if (!q) {
      searchResults.innerHTML = `<p style="padding:8px 12px;color:var(--muted)">Type a class, method, or permission.</p>`;
      return;
    }
    const hits = searchIndex.filter((item) => item.hay.includes(q)).slice(0, 40);
    searchResults.innerHTML = hits.length
      ? hits.map((hit) => `<a class="search-hit" href="${hit.href}"><strong>${esc(hit.title)}</strong><small>${esc(hit.hint)}</small></a>`).join("")
      : `<p style="padding:8px 12px;color:var(--muted)">No matches for “${esc(query)}”.</p>`;
  }

  function openSearch() {
    searchLayer.hidden = false;
    searchInput.value = "";
    runSearch("");
    searchInput.focus();
  }

  function closeSearch() {
    searchLayer.hidden = true;
  }

  menuBtn.addEventListener("click", () => sidebar.classList.toggle("open"));
  searchOpen.addEventListener("click", openSearch);
  document.getElementById("searchClose").addEventListener("click", closeSearch);
  searchLayer.addEventListener("click", (event) => {
    if (event.target === searchLayer) closeSearch();
  });
  searchInput.addEventListener("input", () => runSearch(searchInput.value));
  searchResults.addEventListener("click", (event) => {
    if (event.target.closest("a")) closeSearch();
  });
  window.addEventListener("hashchange", render);
  window.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      if (searchLayer.hidden) openSearch();
      else closeSearch();
    }
    if (event.key === "Escape") closeSearch();
  });

  const themeBtn = document.getElementById("themeBtn");
  const theme = localStorage.getItem("luna-docs-theme");
  if (theme === "light") document.documentElement.dataset.theme = "light";
  themeBtn?.addEventListener("click", () => {
    const next = document.documentElement.dataset.theme === "light" ? "dark" : "light";
    if (next === "dark") delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = "light";
    localStorage.setItem("luna-docs-theme", next);
  });

  buildSidebar();
  render();
})();
