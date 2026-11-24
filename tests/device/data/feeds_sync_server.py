#!/usr/bin/env python3
"""A fake FreshRSS (Google Reader API) and Nextcloud News (v1-3) server for the Feeds sync tests.

Also a fake Linkwarden (/api/v1/..., Bearer "lw-token"): /_lw_add?url= registers a preserved link (readable copy and
PDF); saved links are kept and listed at /_state.

Two feeds with three posts; one post read, one starred. It keeps what the phone changes and shows it at /_state.
User "etesian", password "secret" (anything else is refused). Run: feeds_sync_server.py PORT
"""
import base64
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

USER, PASSWORD, AUTH = "etesian", "secret", "fake-auth-token"
NOW = int(time.time())
FEEDS = {1: {"url": "https://news.example/feed.xml", "title": "Example News"},
         2: {"url": "https://blog.example/atom", "title": "Example Blog"}}
ITEMS = {
    101: {"feed": 1, "title": "Sync first post", "body": "<p>The first post, unread on the server.</p>", "unread": True, "starred": False, "t": NOW - 60},
    102: {"feed": 1, "title": "Sync read post", "body": "<p>Already read on the server.</p>", "unread": False, "starred": False, "t": NOW - 120},
    201: {"feed": 2, "title": "Sync starred post", "body": "<p>Starred on the server.</p>", "unread": True, "starred": True, "t": NOW - 180},
}
STATE = {"subscribed": [], "unsubscribed": [], "logins": 0, "lw_links": []}
LW_TOKEN = "lw-token"


def lw_link(i, l):
    return {"id": i, "url": l["url"], "name": l.get("name", ""), "readable": l["readable"], "pdf": l["pdf"]}
GR = "tag:google.com,2005:reader/item/"


def greader_item(i, it):
    cats = ["user/-/state/com.google/reading-list"]
    if not it["unread"]:
        cats.append("user/-/state/com.google/read")
    if it["starred"]:
        cats.append("user/-/state/com.google/starred")
    f = FEEDS[it["feed"]]
    return {"id": GR + str(i), "title": it["title"], "published": it["t"], "categories": cats,
            "canonical": [{"href": "https://example.test/%d" % i}], "summary": {"content": it["body"]},
            "origin": {"streamId": "feed/%d" % it["feed"], "title": f["title"]}}


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, code, body, ctype="application/json"):
        b = body.encode() if isinstance(body, str) else json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(n).decode() if n else ""

    def gr_ok(self):
        return self.headers.get("Authorization") == "GoogleLogin auth=" + AUTH

    def nc_ok(self):
        return self.headers.get("Authorization") == "Basic " + base64.b64encode(f"{USER}:{PASSWORD}".encode()).decode()

    def do_GET(self):
        u = urlparse(self.path)
        if u.path == "/_lw_add":
            url = parse_qs(u.query)["url"][0]
            STATE["lw_links"].append({"url": url, "readable": "archives/1/1.json", "pdf": "archives/1/1.pdf"})
            return self.send(200, {"id": len(STATE["lw_links"])})
        if u.path.startswith("/api/v1/"):
            if self.headers.get("Authorization") != "Bearer " + LW_TOKEN:
                return self.send(401, {"response": "Unauthorized"})
            q = parse_qs(u.query)
            if u.path == "/api/v1/links":
                s = q.get("searchQueryString", [""])[0]
                return self.send(200, {"response": [lw_link(i + 1, l) for i, l in enumerate(STATE["lw_links"]) if s in l["url"]]})
            if u.path.startswith("/api/v1/archives/"):
                i = int(u.path.rsplit("/", 1)[1])
                if q.get("format") == ["3"]:
                    return self.send(200, {"title": "Linkwarden copy", "content": "<p>LINKWARDEN-COPY The article as Linkwarden preserved it.</p>"})
                if q.get("format") == ["2"]:
                    return self.send(200, "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n", "application/pdf")
        if u.path == "/_state":
            return self.send(200, {"items": ITEMS, "feeds": FEEDS, **STATE})
        if u.path.startswith("/api/greader.php/"):
            if not self.gr_ok():
                return self.send(401, "Unauthorized", "text/plain")
            p = u.path[len("/api/greader.php"):]
            if p == "/reader/api/0/token":
                return self.send(200, "fake-T-token\n", "text/plain")
            if p == "/reader/api/0/subscription/list":
                return self.send(200, {"subscriptions": [{"id": "feed/%d" % k, "title": f["title"], "url": f["url"]} for k, f in FEEDS.items()]})
            if p.startswith("/reader/api/0/stream/contents/"):
                items = sorted(ITEMS.items(), key=lambda kv: -kv[1]["t"])
                return self.send(200, {"items": [greader_item(i, it) for i, it in items if it["feed"] in FEEDS]})
        if u.path.startswith("/index.php/apps/news/api/v1-3/"):
            if not self.nc_ok():
                return self.send(401, {"message": "Unauthorized"})
            p = u.path[len("/index.php/apps/news/api/v1-3"):]
            if p == "/feeds":
                return self.send(200, {"feeds": [{"id": k, "url": f["url"], "title": f["title"]} for k, f in FEEDS.items()]})
            if p == "/items":
                q = parse_qs(u.query)
                only_starred = q.get("type", ["3"])[0] == "2"
                return self.send(200, {"items": [{"id": i, "title": it["title"], "body": it["body"], "url": "https://example.test/%d" % i,
                                                  "pubDate": it["t"], "feedId": it["feed"], "unread": it["unread"], "starred": it["starred"]}
                                                 for i, it in ITEMS.items() if it["feed"] in FEEDS and (it["starred"] or not only_starred)]})
        self.send(404, {"message": "Not found"})

    def do_POST(self):
        u = urlparse(self.path)
        raw = self.body()
        if u.path == "/api/v1/links":
            if self.headers.get("Authorization") != "Bearer " + LW_TOKEN:
                return self.send(401, {"response": "Unauthorized"})
            d = json.loads(raw)
            STATE["lw_links"].append({"url": d["url"], "name": d.get("name", ""), "readable": "pending", "pdf": "pending"})
            return self.send(200, {"response": lw_link(len(STATE["lw_links"]), STATE["lw_links"][-1])})
        if u.path == "/api/greader.php/accounts/ClientLogin":
            f = parse_qs(raw)
            if f.get("Email") == [USER] and f.get("Passwd") == [PASSWORD]:
                STATE["logins"] += 1
                return self.send(200, f"SID={AUTH}\nLSID=none\nAuth={AUTH}\n", "text/plain")
            return self.send(401, "Error=BadAuthentication\n", "text/plain")
        if u.path.startswith("/api/greader.php/"):
            if not self.gr_ok():
                return self.send(401, "Unauthorized", "text/plain")
            f = parse_qs(raw)
            p = u.path[len("/api/greader.php"):]
            if f.get("T") != ["fake-T-token"]:
                return self.send(400, "Bad token", "text/plain")
            if p == "/reader/api/0/edit-tag":
                tag = (f.get("a") or f.get("r"))[0]
                on = "a" in f
                for i in f.get("i", []):
                    n = int(i.rsplit("/", 1)[-1], 16) if "/" in i and not i.rsplit("/", 1)[-1].isdigit() else int(i.rsplit("/", 1)[-1])
                    if n in ITEMS:
                        if tag.endswith("/read"):
                            ITEMS[n]["unread"] = not on
                        if tag.endswith("/starred"):
                            ITEMS[n]["starred"] = on
                return self.send(200, "OK", "text/plain")
            if p == "/reader/api/0/subscription/quickadd":
                url = f["quickadd"][0]
                k = max(FEEDS) + 1
                FEEDS[k] = {"url": url, "title": "Added " + url}
                STATE["subscribed"].append(url)
                return self.send(200, {"query": url, "numResults": 1, "streamId": "feed/%d" % k})
            if p == "/reader/api/0/subscription/edit":
                k = int(f["s"][0].split("/")[1])
                STATE["unsubscribed"].append(FEEDS.pop(k)["url"])
                return self.send(200, "OK", "text/plain")
        if u.path.startswith("/index.php/apps/news/api/v1-3/"):
            if not self.nc_ok():
                return self.send(401, {"message": "Unauthorized"})
            p = u.path[len("/index.php/apps/news/api/v1-3"):]
            data = json.loads(raw or "{}")
            ops = {"/items/read/multiple": ("unread", False), "/items/unread/multiple": ("unread", True),
                   "/items/star/multiple": ("starred", True), "/items/unstar/multiple": ("starred", False)}
            if p in ops:
                key, val = ops[p]
                for n in data.get("itemIds", []):
                    if n in ITEMS:
                        ITEMS[n][key] = val
                return self.send(200, {})
            if p == "/feeds":
                k = max(FEEDS) + 1
                FEEDS[k] = {"url": data["url"], "title": "Added " + data["url"]}
                STATE["subscribed"].append(data["url"])
                return self.send(200, {"feeds": [{"id": k, "url": data["url"], "title": FEEDS[k]["title"]}]})
        self.send(404, {"message": "Not found"})

    def do_DELETE(self):
        u = urlparse(self.path)
        p = u.path[len("/index.php/apps/news/api/v1-3"):]
        if u.path.startswith("/index.php/apps/news/api/v1-3/feeds/") and self.nc_ok():
            STATE["unsubscribed"].append(FEEDS.pop(int(p.split("/")[2]))["url"])
            return self.send(200, {})
        self.send(404, {"message": "Not found"})


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
