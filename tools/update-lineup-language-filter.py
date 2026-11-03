#!/usr/bin/env python3
"""Build a country browsing filter from feed languages and explicit channel overrides."""
import argparse
import datetime
import json
import urllib.request
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FEEDS = "https://iptv-org.github.io/api/feeds.json"


def excluded_ids(metadata, feeds, country, language, overrides):
    canonical = lambda code: "GB" if code.upper() == "UK" else code.upper()
    known = {row["id"] for row in metadata if canonical(row["country"]) == canonical(country)}
    languages = defaultdict(set)
    for feed in feeds:
        languages[feed["channel"]].update(feed.get("languages", []))
    for id_, values in overrides.items():
        if id_ not in known or not values:
            raise ValueError("Language overrides must identify channels in the selected country")
        languages[id_] = set(values)
    # Retain multilingual and unknown-language channels rather than infer a language.
    return sorted(id_ for id_ in known if languages[id_] == {language})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--country", required=True)
    parser.add_argument("--language", required=True)
    parser.add_argument("--overrides", required=True, type=Path)
    parser.add_argument("--retain-category", action="append", default=[])
    parser.add_argument("--feeds", type=Path, help="Use a saved API response instead of downloading it")
    args = parser.parse_args()
    if args.feeds:
        feeds = json.loads(args.feeds.read_text())
    else:
        with urllib.request.urlopen(FEEDS, timeout=30) as response:
            feeds = json.load(response)
    metadata = json.loads((ROOT / "app/src/main/assets/channels.json").read_text())
    overrides = json.loads(args.overrides.read_text())
    ids = excluded_ids(metadata, feeds, args.country, args.language, overrides)
    path = ROOT / "app/src/main/assets/channel-order.json"
    data = json.loads(path.read_text())
    country = "GB" if args.country.upper() == "UK" else args.country.upper()
    policy = data["countries"][country]
    policy["excludedIds"] = ids
    policy["retainCategories"] = args.retain_category
    policy["source"]["languageFilter"] = {"feeds": FEEDS, "language": args.language,
        "retrievedOn": datetime.date.today().isoformat(), "overrides": overrides}
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(f"Configured {len(ids)} known {args.language}-only identities; retained categories: {args.retain_category}")


if __name__ == "__main__":
    main()
