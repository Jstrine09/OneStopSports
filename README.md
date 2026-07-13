# OneStopSports
Sports Data App for every sports enjoyer

**Tests:** `mvn test` runs the fast H2 suite (no Docker needed). `mvn verify -Pintegration` additionally runs `PostgresMigrationIT` against a real, ephemeral `postgres:16-alpine` container (requires a running Docker daemon) — see `CLAUDE.md`'s Testing section for details.
