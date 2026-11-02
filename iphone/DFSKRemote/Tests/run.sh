#!/bin/bash
# Parser self-check on the Mac (no simulator needed).
set -e
cd "$(dirname "$0")/.."
mkdir -p build
swiftc -O -o build/paneltests Sources/Panel.swift Tests/main.swift
./build/paneltests
