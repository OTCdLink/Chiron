This directory contains the top-level Maven project, defining current version and submodules.

This directory makes possible to share sources in a development environment by hardlinking the `modules/chiron` directory. Chiron is a part of OTCdLink Trader, a closed-source project. In order to streamline its development, OTCdLink Trader developers don't use git submodules (not supported by IntelliJ IDEA), nor ship Chiron as a separate project which its own versioning scheme (which would mean shipping SNAPSHOT versions). Hardlinking sounds a bit weird, but it's the best solution so far.

