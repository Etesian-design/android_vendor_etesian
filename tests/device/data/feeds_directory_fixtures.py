#!/usr/bin/env python3
"""Builds the fake feed lists for feeds_directory.sh in DIR, pointing at http://127.0.0.1:PORT.

feeds.db.zip: a rumca-js-shaped SQLite database (linkdatamodel) with two live feeds and a dead one.
all.sources.rss.yaml: an infobubble-shaped list (country, category, addresses).
etesian-test.xml: the feed a found result subscribes to.
Usage: feeds_directory_fixtures.py DIR PORT
"""
import os, sqlite3, sys, zipfile

d, port = sys.argv[1], sys.argv[2]
base = f"http://127.0.0.1:{port}"
os.makedirs(d, exist_ok=True)
db = os.path.join(d, "feeds.db")
if os.path.exists(db):
    os.remove(db)
c = sqlite3.connect(db)
c.execute("CREATE TABLE linkdatamodel (id INTEGER PRIMARY KEY, link TEXT, title TEXT, description TEXT, status_code INTEGER, date_dead_since TIMESTAMP)")
c.executemany("INSERT INTO linkdatamodel (link, title, description, status_code, date_dead_since) VALUES (?,?,?,?,?)", [
    (f"{base}/etesian-test.xml", "Etesian Test Feed", "A feed about swipe gestures and the N9.", 200, None),
    ("https://kitchen.example/rss", "Kitchen Gazette", "Recipes and cooking.", 200, None),
    ("https://dead.example/rss", "Etesian Dead Feed", "Gone.", 404, "2026-01-01"),
])
c.commit(); c.close()
with zipfile.ZipFile(os.path.join(d, "feeds.db.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    z.write(db, "feeds.db")
os.remove(db)
with open(os.path.join(d, "all.sources.rss.yaml"), "w") as f:
    f.write("# fake infobubble list\nTestland:\n  'Science & Tech':\n    - 'https://www.quasarnews.example/feed'\n  Other:\n    - 'https://other.example/rss'\n")
with open(os.path.join(d, "etesian-test.xml"), "w") as f:
    f.write('<?xml version="1.0"?><rss version="2.0"><channel><title>Etesian Test Feed</title>'
            '<item><title>Found and subscribed</title><link>https://example.test/found</link><description>Hello.</description></item>'
            '</channel></rss>')
