#!/usr/bin/env python3
"""Install a CI browser artifact in a fresh isolated game directory."""
import pathlib, sys
from native_browser import host_platform, install
archive = pathlib.Path(sys.argv[1]) / (host_platform() + '.tar.gz')
checksum = archive.with_name(archive.name + '.sha256').read_text().split()[0]
install(archive, pathlib.Path(sys.argv[2]), checksum)
