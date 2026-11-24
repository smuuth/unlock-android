package dev.unlock.reader

/**
 * Selbstständiges Readability-Skript für WebView.evaluateJavascript.
 *
 * Läuft direkt im bereits geparsten DOM von archive.today oder der Wayback Machine:
 * - Erkennt Captchas, „No results“ und laufende Archivierungen.
 * - Isoliert `#CONTENT` (ohne `#HEADER` / Wayback-Leiste).
 * - Unterstützt sowohl semantisches HTML5 (`<article>`, `<p>`, `<h1>`) als auch
 *   archive.today-Snapshots, bei denen `<p>` und `<h1>` in `<div>` umgewandelt sind.
 * - Extrahiert Überschrift, Autor/Datum, Bilder, Zitate, Listen und formatierte Absätze.
 */
object ArticleExtractorScript {

    val JS: String = """
(function() {
  try {
    var bodyText = (document.body && document.body.innerText || '').trim();
    var titleText = (document.title || '').trim();
    var host = (location.hostname || '').toLowerCase();
    var isArchiveToday = /(^|\.)archive\.(ph|is|today|md|li|vn|fo|ec)$/.test(host);
    var isWayback = host === 'web.archive.org';
    var hasArchiveContent = !!document.getElementById('CONTENT');

    // 1. Captcha / Sicherheitsabfrage erkennen
    var hasCaptchaEl = !!document.querySelector(
      '#recaptcha, #g-recaptcha, .g-recaptcha, iframe[src*="recaptcha"], iframe[src*="hcaptcha"], ' +
      'iframe[src*="challenges.cloudflare"], iframe[src*="turnstile"], #cf-challenge-running'
    );
    var looksLikeCaptcha = hasCaptchaEl ||
      /one more step/i.test(bodyText.slice(0, 800)) ||
      /please complete the security check/i.test(bodyText.slice(0, 800)) ||
      /verify you are human/i.test(bodyText.slice(0, 800));
    if (looksLikeCaptcha) {
      return JSON.stringify({ status: 'captcha' });
    }

    // 2. Laufende Archivierung / Submit-Seite / Startformular erkennen
    if (/\/submit(\/|$)/i.test(location.pathname) || /\/wip(\/|$)/i.test(location.pathname) ||
        (isWayback && /\/save(\/|$)/i.test(location.pathname))) {
      return JSON.stringify({ status: 'submitting' });
    }
    if (isArchiveToday && !hasArchiveContent && !!document.getElementById('submiturl')) {
      return JSON.stringify({ status: 'submit_form' });
    }

    // 3. „No results“ (archive.today) oder „not archived“ (Wayback Machine)
    var headSlice = bodyText.slice(0, 1200);
    if ((/no results/i.test(headSlice) && /archive this url/i.test(headSlice)) ||
        (/keine ergebnisse/i.test(headSlice) && /archivieren/i.test(headSlice)) ||
        (isArchiveToday && !hasArchiveContent && !!document.querySelector('a[href*="?url="]') &&
          /\/(newest|oldest|timegate)\//i.test(location.pathname)) ||
        /wayback machine has( not|n't) archived/i.test(headSlice) ||
        /hrm\.\s*the wayback machine/i.test(headSlice)) {
      return JSON.stringify({ status: 'no_results' });
    }
    if (isArchiveToday && !hasArchiveContent) {
      return JSON.stringify({ status: 'not_article' });
    }

    // 4. Hauptbereich eingrenzen: bei archive.today liegt die Seite in #CONTENT
    var root = document.getElementById('CONTENT') || document.body;
    if (!root) return JSON.stringify({ status: 'not_article' });

    function cleanSpace(s) {
      return (s || '').replace(/\u00A0/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function isSelfHidden(el) {
      if (!el || el.nodeType !== 1) return false;
      if (el.hidden || el.getAttribute('aria-hidden') === 'true') return true;
      var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
      if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) {
        return true;
      }
      return false;
    }

    function isHidden(el) {
      for (var cur = el; cur && cur !== document.documentElement; cur = cur.parentElement) {
        if (isSelfHidden(cur)) return true;
        if (cur === root) break;
      }
      return false;
    }

    var NOISE_RE = /\b(nav|navbar|header-bar|masthead|breadcrumb|sidebar|aside|footer|comment|comments|cookie|consent|banner|promo|newsletter|subscribe|subscription|paywall|social|share|sharing|toolbar|related|recommended|more-stories|trending|outbrain|taboola|advert|ad-slot|sponsor|widget|modal|popup|menu|search-box|feature-bar|gpsmodule|google-preferred)\b/i;

    function isNoise(el) {
      if (!el || el.nodeType !== 1) return false;
      var tag = el.tagName;
      if (tag === 'NAV' || tag === 'FOOTER' || tag === 'ASIDE' || tag === 'SCRIPT' ||
          tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'IFRAME' || tag === 'FORM' ||
          tag === 'BUTTON' || tag === 'SVG' || tag === 'TEMPLATE') {
        return true;
      }
      var id = el.id || '';
      if (id === 'HEADER' || id === 'wm-ipp-base' || id === 'donato') return true;
      var cls = (typeof el.className === 'string' ? el.className : '') + ' ' + id + ' ' +
        (el.getAttribute('role') || '') + ' ' + (el.getAttribute('data-area') || '') + ' ' +
        (el.getAttribute('data-testid') || '');
      if (NOISE_RE.test(cls)) {
        if (tag === 'ARTICLE' || tag === 'MAIN' || el.getAttribute('role') === 'main') return false;
        if (cleanSpace(el.innerText).length > 1000 && linkDensity(el) < 0.25) return false;
        return true;
      }
      return false;
    }

    function isInNoise(el) {
      for (var cur = el; cur && cur !== root && cur !== document.body; cur = cur.parentElement) {
        if (isNoise(cur)) return true;
      }
      return false;
    }

    function isInsideLink(el) {
      return !!(el && el.closest && el.closest('a[href]'));
    }

    function linkDensity(el) {
      if (isInsideLink(el)) return 1;
      var textLen = cleanSpace(el.innerText).length;
      if (textLen === 0) return 1;
      var linkLen = 0;
      var links = el.querySelectorAll('a');
      for (var i = 0; i < links.length; i++) {
        linkLen += cleanSpace(links[i].innerText).length;
      }
      return linkLen / textLen;
    }

    var BLOCK_DESC_SEL = 'div, p, article, section, main, ul, ol, li, table, blockquote, figure, header, footer, nav, aside, h1, h2, h3, h4, h5, h6';

    function isLeafTextBlock(el) {
      if (!el || el.nodeType !== 1) return false;
      var tag = el.tagName;
      if (tag !== 'P' && tag !== 'DIV') return false;
      return !el.querySelector(BLOCK_DESC_SEL);
    }

    function getFontSize(el) {
      var s = window.getComputedStyle ? window.getComputedStyle(el) : null;
      return s ? (parseFloat(s.fontSize) || 16) : 16;
    }

    function isBoldWeight(el) {
      var s = window.getComputedStyle ? window.getComputedStyle(el) : null;
      if (!s) return false;
      var fw = s.fontWeight;
      return fw === 'bold' || parseInt(fw, 10) >= 600;
    }

    // 5. Alle Blatt-Textblöcke (<p> oder blattförmige <div>, wie bei archive.today) finden
    //    und deren Eltern-Container nach Textdichte bewerten
    var allLeaves = [];
    var rawBlocks = root.querySelectorAll('p, div');
    for (var i = 0; i < rawBlocks.length; i++) {
      var b = rawBlocks[i];
      if (b.tagName === 'DIV' && !isLeafTextBlock(b)) continue;
      if (isInsideLink(b) || isHidden(b) || isInNoise(b)) continue;
      var t = cleanSpace(b.innerText);
      if (t.length < 40) continue;
      if (linkDensity(b) > 0.55) continue;
      allLeaves.push({ el: b, len: t.length, punct: (t.split(/[,.;:]/).length - 1) });
    }

    // Eltern-Container hochbewerten (auch tief verschachtelte CMS-Wrapper wie bei Vue/Nuxt)
    var candidateMap = [];
    function addScore(container, pts) {
      if (!container || container === root || container === document.body || container === document.documentElement) return;
      if (isHidden(container) || isInNoise(container)) return;
      for (var k = 0; k < candidateMap.length; k++) {
        if (candidateMap[k].el === container) {
          candidateMap[k].score += pts;
          candidateMap[k].count += 1;
          return;
        }
      }
      candidateMap.push({ el: container, score: pts, count: 1 });
    }

    for (var i = 0; i < allLeaves.length; i++) {
      var leaf = allLeaves[i];
      var basePts = Math.min(leaf.len, 450) / 35 + leaf.punct * 0.6;
      var cur = leaf.el.parentElement;
      var depth = 0;
      for (var hop = 0; cur && cur !== root && cur !== document.body && hop < 14; hop++, cur = cur.parentElement) {
        if (isNoise(cur)) break;
        var weight = 1 / (1 + depth * 0.35);
        addScore(cur, basePts * weight);
        depth += (cur.children && cur.children.length > 1) ? 1 : 0.25;
        if (depth > 6) break;
      }
    }

    var bestNode = null;
    var bestScore = -1;
    for (var i = 0; i < candidateMap.length; i++) {
      var c = candidateMap[i];
      if (c.count < 2) continue;
      var ld = linkDensity(c.el);
      if (ld > 0.45) continue;
      var finalScore = c.score * (1 - ld);
      var tag = c.el.tagName;
      if (tag === 'ARTICLE') finalScore *= 1.4;
      else if (tag === 'MAIN' || c.el.getAttribute('role') === 'main') finalScore *= 1.25;
      if (finalScore > bestScore) {
        bestScore = finalScore;
        bestNode = c.el;
      }
    }

    if (!bestNode) {
      bestNode = root.querySelector('article') || root.querySelector('main') || root;
    }

    // Falls der beste Container nur den Body-Text hat, das Titelbild/H1/Vorspann aber darüber liegt:
    // bis zu zwei Ebenen nach oben gehen, solange Link-Dichte niedrig bleibt
    for (var up = 0; up < 2 && bestNode !== root && bestNode.parentElement && bestNode.parentElement !== root; up++) {
      var parent = bestNode.parentElement;
      if (isHidden(parent) || isNoise(parent)) break;
      var parentLd = linkDensity(parent);
      var bestLen = cleanSpace(bestNode.innerText).length;
      var parentLen = cleanSpace(parent.innerText).length;
      var addsH1 = !bestNode.querySelector('h1') && !!parent.querySelector('h1') && parentLd < 0.35;
      var addsHeroOrLead = parentLd < 0.25 && parentLen > bestLen && parentLen <= bestLen * 1.3 &&
        !!parent.querySelector('figure, img') && !bestNode.querySelector('figure');
      var addsLeadSameDensity = parentLd <= linkDensity(bestNode) + 0.03 && parentLd < 0.25 &&
        parentLen > bestLen + 40 && parentLen <= bestLen * 1.25;
      if (addsH1 || addsHeroOrLead || addsLeadSameDensity) {
        bestNode = parent;
      } else {
        break;
      }
    }

    // 6. Titel bestimmen: <h1>, sonst größte Überschrift im Artikel/CONTENT, sonst document.title
    var h1 = bestNode.querySelector('h1') || root.querySelector('h1');
    var titleEl = h1;
    var title = h1 ? cleanSpace(h1.innerText) : '';
    if (!title || title.length < 8) {
      var bestFontSize = 23;
      var headCandidates = (bestNode !== root ? bestNode : root).querySelectorAll('div, span');
      for (var i = 0; i < headCandidates.length && i < 250; i++) {
        var hc = headCandidates[i];
        if (isHidden(hc) || isNoise(hc) || hc.querySelector(BLOCK_DESC_SEL)) continue;
        var fs = getFontSize(hc);
        if (fs > bestFontSize) {
          var ht = cleanSpace(hc.innerText);
          if (ht.length >= 12 && ht.length <= 220 && linkDensity(hc) < 0.3) {
            bestFontSize = fs;
            title = ht;
            titleEl = hc;
          }
        }
      }
    }
    if (!title || title.length < 8) {
      var ogTitle = document.querySelector('meta[property="og:title"]');
      title = cleanSpace((ogTitle && ogTitle.content) || titleText);
      title = title.replace(/\s+[\|\-\u2013\u2014]\s+[^\|\-\u2013\u2014]{2,35}$/, '').trim();
    }

    var byline = null;
    var bylineEl = bestNode.querySelector('[rel="author"], [itemprop="author"], [class*="byline"], [class*="author"], [data-testid*="byline"]') ||
                   root.querySelector('[rel="author"], [itemprop="author"], [class*="byline"]');
    if (bylineEl) {
      var bt = cleanSpace(bylineEl.innerText);
      if (bt.length >= 3 && bt.length <= 140) byline = bt;
    }

    var publishedDate = null;
    var timeEl = bestNode.querySelector('time') || root.querySelector('time');
    if (timeEl) {
      var tt = cleanSpace(timeEl.innerText || timeEl.getAttribute('datetime'));
      if (tt.length >= 4 && tt.length <= 80) publishedDate = tt;
    }

    // Sprache des Artikels (für die Silbentrennung in der Leseansicht).
    // archive.today übernimmt das lang-Attribut nicht immer, daher mehrere Quellen.
    var language = null;
    var langEl = bestNode.closest && bestNode.closest('[lang]');
    var langCandidates = [
      langEl && langEl.getAttribute('lang'),
      document.documentElement.getAttribute('lang'),
      (document.querySelector('meta[http-equiv="content-language" i]') || {}).content,
      (document.querySelector('meta[property="og:locale"]') || {}).content
    ];
    for (var li = 0; li < langCandidates.length; li++) {
      var lc = cleanSpace(langCandidates[li]).replace(/_/g, '-');
      if (/^[a-z]{2,3}(-[a-z0-9]{2,8})*$/i.test(lc)) { language = lc; break; }
    }

    // 7. Inline-Formatierung (Fett, Kursiv, Links) extrahieren
    function extractSpans(node, state, out) {
      if (!node) return;
      if (node.nodeType === 3) {
        var txt = (node.nodeValue || '').replace(/\u00A0/g, ' ').replace(/\s+/g, ' ');
        if (txt) {
          out.push({
            text: txt,
            bold: !!state.bold,
            italic: !!state.italic,
            link: state.link || null
          });
        }
        return;
      }
      if (node.nodeType !== 1 || isHidden(node) || isNoise(node)) return;
      var tag = node.tagName;
      if (tag === 'BR') {
        out.push({ text: '\n', bold: false, italic: false, link: null });
        return;
      }
      var nextState = {
        bold: state.bold || tag === 'STRONG' || tag === 'B',
        italic: state.italic || tag === 'EM' || tag === 'I',
        link: state.link
      };
      if (tag === 'A' && node.getAttribute('href')) {
        try {
          var resolved = new URL(node.getAttribute('href'), location.href).href;
          if (/^https?:\/\//i.test(resolved)) nextState.link = resolved;
        } catch (e) {}
      }
      for (var c = node.firstChild; c; c = c.nextSibling) {
        extractSpans(c, nextState, out);
      }
    }

    function normalizeSpans(spans) {
      var merged = [];
      for (var i = 0; i < spans.length; i++) {
        var s = spans[i];
        if (!s.text) continue;
        var prev = merged.length ? merged[merged.length - 1] : null;
        if (prev && prev.bold === s.bold && prev.italic === s.italic && prev.link === s.link) {
          prev.text += s.text;
        } else {
          merged.push({ text: s.text, bold: s.bold, italic: s.italic, link: s.link });
        }
      }
      if (merged.length) {
        merged[0].text = merged[0].text.replace(/^\s+/, '');
        merged[merged.length - 1].text = merged[merged.length - 1].text.replace(/\s+$/, '');
      }
      return merged.filter(function(s) { return s.text.length > 0; });
    }

    function resolveImgSrc(img) {
      var raw = img.currentSrc || img.getAttribute('src') || img.getAttribute('data-src') || '';
      if (!raw || /^data:image\/(svg|gif)/i.test(raw)) return null;
      try {
        var u = new URL(raw, location.href).href;
        if (!/^https?:\/\//i.test(u)) return null;
        return u;
      } catch (e) {
        return null;
      }
    }

    function isGoodImage(img) {
      var rect = img.getBoundingClientRect ? img.getBoundingClientRect() : null;
      var w = (img.naturalWidth || 0) || (rect ? rect.width : 0) || parseInt(img.getAttribute('width') || '0', 10);
      var h = (img.naturalHeight || 0) || (rect ? rect.height : 0) || parseInt(img.getAttribute('height') || '0', 10);
      if (w > 0 && w < 140) return false;
      if (h > 0 && h < 90) return false;
      return true;
    }

    // 8. Blöcke in DOM-Reihenfolge einsammeln
    var blocks = [];
    var seenTexts = {};
    var seenImgs = {};
    var wordCount = 0;

    var BOILERPLATE_RE = /^(spiegel bei google bevorzugen|prefer the guardian on google|add .+ as a preferred source|see more of our coverage|support the guardian|fund independent journalism|make a year-end gift|print subscriptions|newsletters|listen\s*[·•]|share|bookmark|zur merkliste|bild vergrößern|sign in|subscribe|log in|anmelden|abonnement|skip to main content|skip to navigation)$/i;

    function pushParagraph(el) {
      if (isInsideLink(el)) return;
      var raw = cleanSpace(el.innerText);
      if (raw.length < 25) return;
      if (raw === title || raw === byline || raw === publishedDate) return;
      if (BOILERPLATE_RE.test(raw)) return;
      if (linkDensity(el) > 0.65 && raw.length < 150) return;
      if (seenTexts[raw]) return;
      seenTexts[raw] = true;

      var collected = [];
      extractSpans(el, {}, collected);
      var spans = normalizeSpans(collected);
      if (!spans.length) return;
      wordCount += raw.split(/\s+/).length;
      blocks.push({ type: 'paragraph', spans: spans });
    }

    function pushImage(img, caption) {
      var src = resolveImgSrc(img);
      if (!src || seenImgs[src] || !isGoodImage(img)) return;
      seenImgs[src] = true;
      var cap = cleanSpace(caption || img.getAttribute('alt') || '');
      if (cap.length < 4 || cap.length > 350 || cap === title) cap = null;
      blocks.push({ type: 'image', src: src, caption: cap });
    }

    function walk(node) {
      if (!node || node.nodeType !== 1) return;
      if (node === titleEl || node === h1 || isSelfHidden(node) || isNoise(node)) return;

      var tag = node.tagName;
      if (tag === 'FIGURE') {
        var img = node.querySelector('img');
        var fc = node.querySelector('figcaption');
        var capText = fc ? fc.innerText : cleanSpace(node.innerText);
        if (img) pushImage(img, capText || null);
        return;
      }
      if (tag === 'IMG') {
        pushImage(node, null);
        return;
      }
      if (tag === 'H2' || tag === 'H3' || tag === 'H4') {
        if (isInsideLink(node)) return;
        var ht = cleanSpace(node.innerText);
        if (ht.length >= 3 && ht.length <= 220 && ht !== title && !BOILERPLATE_RE.test(ht) && !seenTexts[ht]) {
          seenTexts[ht] = true;
          blocks.push({ type: 'heading', level: parseInt(tag.charAt(1), 10), text: ht });
        }
        return;
      }
      if (tag === 'BLOCKQUOTE') {
        var qt = cleanSpace(node.innerText);
        if (qt.length >= 15 && !seenTexts[qt]) {
          seenTexts[qt] = true;
          wordCount += qt.split(/\s+/).length;
          blocks.push({ type: 'quote', text: qt });
        }
        return;
      }
      if (tag === 'UL' || tag === 'OL') {
        if (linkDensity(node) > 0.6) return;
        var lis = node.children;
        var items = [];
        for (var i = 0; i < lis.length; i++) {
          if (lis[i].tagName !== 'LI') continue;
          var lt = cleanSpace(lis[i].innerText);
          if (lt.length >= 8 && !BOILERPLATE_RE.test(lt)) items.push(lt);
        }
        if (items.length >= 2) {
          wordCount += items.join(' ').split(/\s+/).length;
          blocks.push({ type: 'list', ordered: tag === 'OL', items: items });
        }
        return;
      }
      if (tag === 'P') {
        pushParagraph(node);
        return;
      }
      if (tag === 'DIV' && isLeafTextBlock(node)) {
        if (isInsideLink(node)) return;
        var dt = cleanSpace(node.innerText);
        if (dt.length >= 4 && dt.length <= 200 && getFontSize(node) >= 21 && isBoldWeight(node) &&
            dt !== title && !BOILERPLATE_RE.test(dt) && linkDensity(node) < 0.3 && !seenTexts[dt]) {
          seenTexts[dt] = true;
          blocks.push({ type: 'heading', level: 2, text: dt });
          return;
        }
        pushParagraph(node);
        return;
      }

      for (var c = node.firstChild; c; c = c.nextSibling) {
        walk(c);
      }
    }

    walk(bestNode);

    // Falls kein Bild im bestNode lag (z.B. Hero-Bild eine Ebene höher bei archive.today),
    // das erste große Bild aus #CONTENT voranstellen
    var hasImg = false;
    for (var b = 0; b < blocks.length; b++) {
      if (blocks[b].type === 'image') { hasImg = true; break; }
    }
    if (!hasImg) {
      var imgs = root.querySelectorAll('img');
      for (var k = 0; k < imgs.length && k < 15; k++) {
        var im = imgs[k];
        if (isHidden(im) || isInNoise(im)) continue;
        var src = resolveImgSrc(im);
        if (src && isGoodImage(im)) {
          var cap = cleanSpace(im.getAttribute('alt') || '');
          if (cap.length < 4 || cap.length > 350 || cap === title) cap = null;
          blocks.unshift({ type: 'image', src: src, caption: cap });
          break;
        }
      }
    }

    var paraCount = 0;
    for (var b = 0; b < blocks.length; b++) {
      if (blocks[b].type === 'paragraph') paraCount++;
    }
    if (paraCount < 2 && wordCount < 60) {
      return JSON.stringify({ status: 'not_article' });
    }

    return JSON.stringify({
      status: 'article',
      article: {
        title: title || titleText,
        byline: byline,
        publishedDate: publishedDate,
        language: language,
        readingTimeMinutes: Math.max(1, Math.round(wordCount / 220)),
        blocks: blocks
      }
    });
  } catch (e) {
    return JSON.stringify({ status: 'not_article', error: String(e) });
  }
})();
""".trimIndent()
}
