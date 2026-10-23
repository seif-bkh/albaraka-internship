#!/usr/bin/env python3
"""Fail if any YAML mapping has duplicate keys (SnakeYAML/Spring Boot refuses them
at startup; unit tests do not load the main application.yml, so lint it here)."""
import sys

import yaml


class Strict(yaml.SafeLoader):
    pass


def _mapping(loader, node):
    keys = [loader.construct_object(k) for k, _ in node.value]
    dupes = sorted({k for k in keys if keys.count(k) > 1})
    if dupes:
        raise ValueError(f"duplicate keys {dupes} at line {node.start_mark.line + 1}")
    return loader.construct_mapping(node)


Strict.add_constructor("tag:yaml.org,2002:map", _mapping)

rc = 0
for path in sys.argv[1:]:
    try:
        with open(path, encoding="utf-8") as fh:
            list(yaml.load_all(fh, Strict))
        print(f"ok   {path}")
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL {path}: {exc}")
        rc = 1
sys.exit(rc)
