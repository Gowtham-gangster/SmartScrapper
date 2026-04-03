/* eslint-disable no-console */
const express = require("express");
const puppeteer = require("puppeteer");

const app = express();

const PORT = process.env.PORT ? Number(process.env.PORT) : 3001;
const HEADLESS = process.env.HEADLESS ? String(process.env.HEADLESS) : "new";
const NAV_TIMEOUT_MS = process.env.NAV_TIMEOUT_MS ? Number(process.env.NAV_TIMEOUT_MS) : 30000;
const PAGE_SIZE_DEFAULT = process.env.PAGE_SIZE ? Number(process.env.PAGE_SIZE) : 20;

let browserPromise = null;

async function getBrowser() {
  if (browserPromise) return browserPromise;

  browserPromise = puppeteer.launch({
    headless: HEADLESS,
    args: ["--no-sandbox", "--disable-setuid-sandbox"]
  });

  return browserPromise;
}

function normalizeText(t) {
  if (t === null || t === undefined) return null;
  const s = String(t).replace(/\s+/g, " ").trim();
  return s.length ? s : null;
}

function looksLikeUrl(s) {
  try {
    if (!s) return false;
    // eslint-disable-next-line no-new
    new URL(s);
    return true;
  } catch {
    return false;
  }
}

function buildTargetUrl(type, query, page) {
  // If user passed a full URL, scrape that directly.
  if (looksLikeUrl(query)) {
    const u = new URL(query);

    // Best-effort updates for common pagination params per site type.
    // If none exist, we fall back to `page`.
    if (type === "products" && u.searchParams.has("_pgn")) {
      u.searchParams.set("_pgn", String(page));
    } else if (type === "news" && u.searchParams.has("s")) {
      // DuckDuckGo-like offset pagination
      const offset = (page - 1) * 30;
      u.searchParams.set("s", String(offset));
    } else if (type === "research" && u.searchParams.has("start")) {
      const start = (page - 1) * 10;
      u.searchParams.set("start", String(start));
    } else if (u.searchParams.has("page")) {
      u.searchParams.set("page", String(page));
    } else {
      u.searchParams.set("page", String(page));
    }

    return { url: u.toString(), pagingMode: "url" };
  }

  const encoded = encodeURIComponent(query);

  // Heuristic default targets (useful when `query` is a raw search term)
  if (type === "news") {
    // DuckDuckGo HTML results (fast, works without API keys).
    // Pagination uses `s` (offset) for older results.
    const offset = (page - 1) * 30;
    return { url: `https://html.duckduckgo.com/html/?q=${encoded}&s=${offset}`, pagingMode: "ddg_offset" };
  }

  if (type === "products") {
    // eBay search results listing
    const pgn = page > 0 ? page : 1;
    return { url: `https://www.ebay.com/sch/i.html?_nkw=${encoded}&_pgn=${pgn}`, pagingMode: "ebay_pgn" };
  }

  if (type === "research") {
    // arXiv search results
    const start = (page - 1) * 10;
    return { url: `https://arxiv.org/search/?query=${encoded}&searchtype=all&abstracts=show&order=-announced_date_first&start=${start}`, pagingMode: "arxiv_start" };
  }

  throw new Error("Unknown scrape type");
}

function buildError(status, message, details) {
  return { status, message, ...(details ? { details } : {}) };
}

async function scrapePage({ type, url, pageSize }) {
  const browser = await getBrowser();
  const page = await browser.newPage();
  page.setDefaultNavigationTimeout(NAV_TIMEOUT_MS);

  try {
    await page.goto(url, { waitUntil: "networkidle2" });
    await page.waitForSelector("body", { timeout: NAV_TIMEOUT_MS });

    // Extract candidates using generic selectors.
    // Each scraper tries best-effort to fill the required fields.
    const result = await page.evaluate(
      ({ type: scrapeType, pageSize: max }) => {
        const normalizeText = (t) => {
          if (t === null || t === undefined) return null;
          const s = String(t).replace(/\s+/g, " ").trim();
          return s.length ? s : null;
        };

        const pickFirstText = (el, selectors) => {
          for (const sel of selectors) {
            const found = el.querySelector(sel);
            const v = normalizeText(found ? found.textContent : null);
            if (v) return v;
          }
          return null;
        };

        const pickFirstAttr = (el, selectors, attr) => {
          for (const sel of selectors) {
            const found = el.querySelector(sel);
            const v = found ? found.getAttribute(attr) : null;
            const n = normalizeText(v);
            if (n) return n;
          }
          return null;
        };

        const pickFirstHref = (el) => {
          const a = el.querySelector("a[href]");
          return a ? a.href : null;
        };

        const parseDate = (text) => {
          if (!text) return null;
          // Very light heuristic: YYYY-MM-DD, YYYY/MM/DD, or Month DD, YYYY.
          const iso = text.match(/\b(20\d{2})-(\d{2})-(\d{2})\b/);
          if (iso) return `${iso[1]}-${iso[2]}-${iso[3]}`;
          return null;
        };

        const toItems = (candidates) => {
          const items = [];
          for (const c of candidates) {
            if (items.length >= max) break;

            const title =
              pickFirstText(c, ["h1", "h2", "h3", "h4", ".title", "a"]) ||
              normalizeText(c.textContent && c.textContent.length ? c.textContent.slice(0, 140) : null);

            const link = pickFirstHref(c);

            if (scrapeType === "news") {
              const source =
                pickFirstText(c, [".source", ".publisher", ".site", ".media", "span"]) ||
                (link ? new URL(link).hostname : null);
              const summary = pickFirstText(c, [".summary", ".snippet", ".description", "p"]) || null;
              const dateText = pickFirstText(c, ["time", ".date", ".published", ".pubDate", "span"]);
              const publishedDate =
                (c.querySelector("time") && c.querySelector("time").getAttribute("datetime")) ||
                parseDate(dateText) ||
                dateText ||
                null;

              if (title && link) {
                items.push({
                  title: title,
                  source: source,
                  date: normalizeText(publishedDate),
                  link: link,
                  summary: summary
                });
              }
              continue;
            }

            if (scrapeType === "products") {
              const productName = pickFirstText(c, [".product-title", "h1", "h2", "h3", "a", ".title", ".name"]) || null;
              const link = pickFirstHref(c);

              // Price heuristic
              const priceText =
                pickFirstText(c, [".price", "[class*=price]", "span", "div"]) ||
                null;
              const priceMatch = priceText ? priceText.match(/([$€£]\s?[\d,.]+)/) : null;
              const price = priceMatch ? priceMatch[1].replace(/\s+/g, " ") : null;

              const ratingText = pickFirstText(c, [".rating", "[class*=rating]", "[aria-label*=rating]"]) || null;
              const rating = ratingText || null;

              const reviewsText = pickFirstText(c, [".reviews", "[class*=reviews]", ".review-count"]) || null;
              const reviews = reviewsText || null;

              if (productName && link) {
                items.push({
                  productName,
                  price,
                  rating,
                  reviews,
                  link
                });
              }
              continue;
            }

            if (scrapeType === "research") {
              const titleText = pickFirstText(c, ["h1", "h2", "h3", ".title", "a"]) || null;
              const link = pickFirstHref(c) || null;

              const authors =
                normalizeText(pickFirstText(c, [".authors", "[class*=authors]", ".author"])) ||
                null;

              const yearMatch =
                titleText ? titleText.match(/\b(19\d{2}|20\d{2})\b/) : null;
              const year = yearMatch ? yearMatch[1] : null;

              const abstract =
                pickFirstText(c, [".abstract", ".summary", "[class*=abstract]", "p"]) || null;

              if (titleText && link) {
                items.push({
                  title: titleText,
                  authors,
                  year,
                  abstract,
                  link
                });
              }
              continue;
            }
          }
          return items;
        };

        // Candidate pool by type
        let candidates = [];
        if (scrapeType === "news") {
          candidates = Array.from(document.querySelectorAll("article, .story, .news, .result, .card"));
        } else if (scrapeType === "products") {
          candidates = Array.from(document.querySelectorAll("article, .product, .item, .s-item, .card"));
        } else if (scrapeType === "research") {
          candidates = Array.from(document.querySelectorAll("article, .entry, .arxiv-result, .paper, li"));
        }

        // De-dupe by link
        const items = toItems(candidates);
        const seen = new Set();
        const unique = [];
        for (const it of items) {
          const key = it.link || JSON.stringify(it);
          if (!seen.has(key)) {
            seen.add(key);
            unique.push(it);
          }
        }
        return unique.slice(0, max);
      },
      { type, pageSize }
    );

    // Decide if there might be more by looking for "next" UI (best-effort).
    const hasMore = await page.evaluate(() => {
      const next =
        document.querySelector("a[rel=next], a.next, a[aria-label*=Next i], button.next, .next a") ||
        null;
      if (!next) return false;
      const disabled = next.getAttribute("disabled") || next.getAttribute("aria-disabled");
      if (disabled && String(disabled).toLowerCase() === "true") return false;
      return true;
    });

    return { items: result, hasMore };
  } finally {
    await page.close();
  }
}

async function handleScrape(req, res, type) {
  const query = req.query.query;
  const page = req.query.page ? Number(req.query.page) : 1;
  const maxPages = req.query.maxPages ? Number(req.query.maxPages) : 1;
  const pageSize = req.query.pageSize ? Number(req.query.pageSize) : PAGE_SIZE_DEFAULT;

  if (!query || String(query).trim().length < 2) {
    return res.status(400).json(buildError(400, "Missing or invalid `query` parameter."));
  }
  if (Number.isNaN(page) || page < 1) {
    return res.status(400).json(buildError(400, "Invalid `page` parameter (must be >= 1)."));
  }
  if (Number.isNaN(maxPages) || maxPages < 1) {
    return res.status(400).json(buildError(400, "Invalid `maxPages` parameter (must be >= 1)."));
  }
  if (Number.isNaN(pageSize) || pageSize < 1) {
    return res.status(400).json(buildError(400, "Invalid `pageSize` parameter (must be >= 1)."));
  }

  try {
    const items = [];
    let hasMore = true;

    for (let p = page; p < page + maxPages; p++) {
      if (!hasMore) break;

      const { url } = buildTargetUrl(type, String(query), p);

      const scraped = await scrapePage({ type, url, pageSize });
      items.push(...scraped.items);
      hasMore = scraped.hasMore;

      // Early exit if we already collected enough
      if (items.length >= pageSize * maxPages) break;
    }

    return res.json({
      items,
      page,
      maxPages,
      returnedCount: items.length,
      hasMore
    });
  } catch (err) {
    console.error(err);
    return res.status(500).json(buildError(500, "Scraping failed", { error: err.message }));
  }
}

app.get("/scrape/news", (req, res) => handleScrape(req, res, "news"));
app.get("/scrape/products", (req, res) => handleScrape(req, res, "products"));
app.get("/scrape/research", (req, res) => handleScrape(req, res, "research"));

app.get("/health", (_req, res) => res.json({ ok: true }));

app.listen(PORT, () => {
  console.log(`Puppeteer microservice listening on http://localhost:${PORT}`);
});

process.on("SIGINT", async () => {
  try {
    const b = browserPromise ? await browserPromise : null;
    if (b) await b.close();
  } catch {
    // ignore
  } finally {
    process.exit(0);
  }
});

