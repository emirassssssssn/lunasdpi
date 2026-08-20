(() => {
  const docs = window.LUNA_DOCS;
  const sidebar = document.getElementById("sidebar");
  const main = document.getElementById("content");
  const outline = document.getElementById("outline");
  const menuBtn = document.getElementById("menuBtn");
  const searchLayer = document.getElementById("searchLayer");
  const searchOpen = document.getElementById("searchOpen");
  const searchInput = document.getElementById("searchInput");
  const searchResults = document.getElementById("searchResults");
  const navScrim = document.getElementById("navScrim");
  const colorSchemeMeta = document.querySelector('meta[name="color-scheme"]');

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

  function codeBlock(source) {
    return `<div class="code"><button class="copy-btn" type="button">Copy</button><pre>${highlightLua(source)}</pre></div>`;
  }

  function memberId(cls, member) {
    return `${cls.id}.${member.name}`;
  }

  function allMembers(cls) {
    const list = [];
    (cls.props || []).forEach((p) => list.push({ ...p, kind: p.kind || "prop" }));
    (cls.sections || []).forEach((section) => {
      section.members.forEach((m) => list.push(m));
    });
    return list;
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
    const examples = (member.examples || []).map((ex) => codeBlock(ex)).join("");
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

  function memberIndex(cls) {
    const props = cls.props || [];
    const methods = [];
    (cls.sections || []).forEach((section) => {
      section.members.forEach((m) => {
        if (m.kind !== "prop") methods.push(m);
      });
    });
    if (!props.length && !methods.length) return "";
    const chips = (items, prefix) => items.map((item) => `<a href="#${esc(memberId(cls, item))}">${prefix}${esc(item.name)}</a>`).join("");
    return `
      <div class="member-index">
        <h2 class="section-title">Table of contents</h2>
        ${props.length ? `<h3 class="section-title" style="font-size:16px;margin-top:8px">Properties</h3><div class="chip-row">${chips(props, ".")}</div>` : ""}
        ${methods.length ? `<h3 class="section-title" style="font-size:16px;margin-top:8px">Methods</h3><div class="chip-row">${chips(methods, ":")}</div>` : ""}
      </div>`;
  }

  function renderOutline(page) {
    if (!outline) return;
    if (page.type !== "class") {
      outline.innerHTML = "";
      return;
    }
    const cls = page.data;
    const links = allMembers(cls).map((m) => {
      const prefix = m.kind === "prop" ? "." : ":";
      return `<a href="#${esc(memberId(cls, m))}">${prefix}${esc(m.name)}</a>`;
    });
    outline.innerHTML = links.length ? `<h3>On this page</h3>${links.join("")}` : "";
  }

  function renderClass(cls) {
    const props = (cls.props || [])
      .map((p) => `<tr id="${esc(memberId(cls, p))}"><td>.${esc(p.name)}</td><td>${typeHtml(p.type)}</td><td>${esc(p.desc || "")} ${badges(p)}</td></tr>`)
      .join("");
    const propTable = props
      ? `<h2 class="section-title">Properties</h2><table class="prop-table"><thead><tr><th>Name</th><th>Type</th><th>Description</th></tr></thead><tbody>${props}</tbody></table>`
      : "";
    const construct = cls.construct
      ? `<div class="construct"><h3>Constructor</h3><p>${esc(cls.construct)}</p>${cls.constructExample ? codeBlock(cls.constructExample) : ""}</div>`
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
      ${memberIndex(cls)}
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

  function bindCopy() {
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
  }

  function render() {
    const id = activeId();
    const page = pages.get(id) || pages.get("welcome");
    sidebar.querySelectorAll("a[href^='#']").forEach((a) => {
      a.classList.toggle("active", a.getAttribute("href") === `#${page.data.id}`);
    });
    main.innerHTML = page.type === "class" ? renderClass(page.data) : renderGuide(page.data);
    renderOutline(page);
    bindCopy();
    const member = memberFromHash();
    if (member) {
      const el = document.getElementById(member);
      if (el) el.scrollIntoView({ block: "start" });
    } else {
      window.scrollTo(0, 0);
    }
    setNav(false);
    closeSearch();
    document.title = `${page.data.title || page.data.name} | Lunas DPI Lua API`;
  }

  function kindClass(kind) {
    if (kind === "T") return "kind t";
    if (kind === "G") return "kind g";
    return "kind";
  }

  function buildSidebar() {
    const groups = [
      ["Guide", docs.guides.map((g) => ({ href: `#${g.id}`, label: g.title, kind: "G" }))],
      ["Classes", docs.classes.filter((c) => (c.kind || "class") === "class").map((c) => ({ href: `#${c.id}`, label: c.name, kind: "C" }))],
      ["Typedefs", docs.classes.filter((c) => c.kind === "typedef").map((c) => ({ href: `#${c.id}`, label: c.name, kind: "T" }))],
    ];
    sidebar.innerHTML = `
      <div class="pkg">luna <small>api_level 1</small></div>
      <input class="sidebar-filter" id="sidebarFilter" type="search" placeholder="Filter" />
      ${groups
        .filter(([, items]) => items.length)
        .map(([title, items]) => `<h3>${title}</h3>${items.map((item) => `<a href="${item.href}" data-label="${esc(item.label.toLowerCase())}"><span class="${kindClass(item.kind)}">${item.kind}</span>${esc(item.label)}</a>`).join("")}`)
        .join("")}`;
    sidebar.querySelector("#sidebarFilter").addEventListener("input", (event) => {
      const q = event.target.value.trim().toLowerCase();
      sidebar.querySelectorAll("a[data-label]").forEach((a) => {
        a.hidden = q !== "" && !a.dataset.label.includes(q);
      });
    });
    sidebar.addEventListener("click", (event) => {
      if (event.target.closest("a[href^='#']")) setNav(false);
    });
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
      searchResults.innerHTML = `<p style="padding:8px 12px;color:var(--muted)">Search a class, method, or permission.</p>`;
      return;
    }
    const hits = searchIndex.filter((item) => item.hay.includes(q)).slice(0, 40);
    searchResults.innerHTML = hits.length
      ? hits.map((hit) => `<a class="search-hit" href="${hit.href}"><strong>${esc(hit.title)}</strong><small>${esc(hit.hint)}</small></a>`).join("")
      : `<p style="padding:8px 12px;color:var(--muted)">No matches for “${esc(query)}”.</p>`;
  }

  function setNav(open) {
    sidebar.classList.toggle("open", open);
    document.body.classList.toggle("nav-open", open);
    if (navScrim) navScrim.hidden = !open;
    menuBtn.setAttribute("aria-expanded", open ? "true" : "false");
  }

  function openSearch() {
    setNav(false);
    if (!searchLayer.open) searchLayer.showModal();
    document.body.classList.add("dialog-open");
    searchInput.value = "";
    runSearch("");
    requestAnimationFrame(() => searchInput.focus());
  }

  function closeSearch() {
    document.body.classList.remove("dialog-open");
    if (searchLayer.open) searchLayer.close();
  }

  function currentScheme() {
    const pinned = colorSchemeMeta.content;
    if (pinned === "light" || pinned === "dark") return pinned;
    return matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }

  function pinScheme(next) {
    colorSchemeMeta.content = next;
    document.documentElement.classList.remove("theme-light", "theme-dark");
    document.documentElement.classList.add(`theme-${next}`);
    localStorage.setItem("color-scheme", next);
  }

  menuBtn.addEventListener("click", () => setNav(!sidebar.classList.contains("open")));
  navScrim?.addEventListener("click", () => setNav(false));
  searchOpen.addEventListener("click", openSearch);
  document.getElementById("searchClose").addEventListener("click", closeSearch);
  searchLayer.addEventListener("click", (event) => {
    if (event.target === searchLayer) closeSearch();
  });
  searchLayer.addEventListener("close", () => document.body.classList.remove("dialog-open"));
  searchInput.addEventListener("input", () => runSearch(searchInput.value));
  searchResults.addEventListener("click", (event) => {
    if (event.target.closest("a")) closeSearch();
  });
  window.addEventListener("hashchange", render);
  window.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      if (searchLayer.open) closeSearch();
      else openSearch();
    }
  });
  document.getElementById("themeBtn").addEventListener("click", () => {
    pinScheme(currentScheme() === "dark" ? "light" : "dark");
  });

  buildSidebar();
  render();
})();
