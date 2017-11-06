#!/usr/bin/env bash

# Link to mothership project.
# High Sierra's APFS doesn't support hardlinking at all.
# So we need to run this once per user session before using git.
# Prerequisite: brew install bindfs
# Unmounting happens with normal umount.

sudo bindfs ~/Projects/OTCdLink.trader/maven-modules/chiron modules/chiron

