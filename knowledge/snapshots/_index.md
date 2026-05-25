# Snapshots Index

## Overview

This directory stores version snapshots of the knowledge base.
Each snapshot is a full copy of the `current/` directory at a point in time.

## Snapshots

| Version | Date | Trigger | Description |
|---------|------|---------|-------------|
| 2026-05-25_v1.0 | 2026-05-25 | merge | Pre-merge snapshot for 4 deltas (coroutine-refactor, rename-textutil-to-textutils, refactor-file-io, misc-fixes) |
| (none yet) | | | |

## Creating Snapshots

Run the archive-knowledge pipeline to create a snapshot when:
- A major feature is completed
- A release branch is cut
- Architecture changes are merged
