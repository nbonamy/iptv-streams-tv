#!/usr/bin/env python3
"""Reconcile a captured numbered lineup with provider identities; never invent feeds."""
import argparse
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def normalized(value):
    return "".join(c for c in unicodedata.normalize("NFD", value).lower() if c.isalnum() or c == "+")


def country_code(value):
    code = value.upper()
    return "GB" if code == "UK" else code


def channel_name(value):
    # Quality suffixes identify the same channel; regions and subchannels do not.
    value = re.sub(r"\s*\((?:HD|SD)(?: Feed)?\)$", "", value, flags=re.I)
    return re.sub(r"\s+(?:HD|SD)(?: \(US\))?$", "", value, flags=re.I).strip()


def reconcile(rows, metadata, country, matches):
    country = country_code(country)
    by_id = {item["id"]: item for item in metadata if country_code(item["country"]) == country}
    by_name = {}
    for item in by_id.values():
        by_name.setdefault(normalized(item["name"]), []).append(item["id"])
    explicit = {normalized(name): ids for name, ids in matches.items()}
    for ids in explicit.values():
        if not ids or any(id_ not in by_id for id_ in ids):
            raise ValueError("Explicit matches must reference existing IDs in the selected country")
    output, assigned = [], {}
    for row in sorted(rows, key=lambda item: item["number"]):
        if not isinstance(row["number"], int) or row["number"] <= 0:
            raise ValueError("Expected positive integer channel numbers")
        name = channel_name(row["name"])
        ids = explicit.get(normalized(name))
        if ids is None:
            candidates = by_name.get(normalized(name), [])
            ids = candidates if len(candidates) == 1 else []
        new_ids = [id_ for id_ in ids if id_ not in assigned]
        if new_ids:
            entry = {"number": row["number"], "name": row["name"], "ids": new_ids, "aliases": []}
            output.append(entry)
            for id_ in new_ids:
                assigned[id_] = entry
        for id_ in ids:
            entry = assigned[id_]
            for alias in (row["name"], name, by_id[id_]["name"]):
                if alias != entry["name"] and alias not in entry["aliases"]:
                    entry["aliases"].append(alias)
    return [{k: v for k, v in entry.items() if k != "aliases" or v} for entry in output]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lineup", required=True, type=Path)
    parser.add_argument("--matches", required=True, type=Path)
    parser.add_argument("--country", required=True)
    args = parser.parse_args()
    captured = json.loads(args.lineup.read_text())
    metadata = json.loads((ROOT / "app/src/main/assets/channels.json").read_text())
    country = country_code(args.country)
    channels = reconcile(captured["channels"], metadata, country, json.loads(args.matches.read_text()))
    if not channels:
        raise ValueError("No verified channel identities matched")
    path = ROOT / "app/src/main/assets/channel-order.json"
    data = json.loads(path.read_text())
    data["countries"][country] = {
        "source": captured["source"] | {"sourceRows": len(captured["channels"]), "duplicatePolicy": "lowest channel number per identity"},
        "channels": channels,
    }
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(f"Imported {len(channels)} lineup entries for {country}, with {sum(len(c['ids']) for c in channels)} verified provider IDs.")


if __name__ == "__main__":
    main()
