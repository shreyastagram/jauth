#!/bin/bash
# ============================================================
# FixHomi Auth Service - Startup Script
# ============================================================
# This script loads environment variables from .env file
# and starts the Spring Boot application with the configured profile.
#
# Usage:
#   ./run.sh          # Start with profile from .env (default: prod)
#   ./run.sh local    # Local development: file H2, stub SMS/email, seeded admin
#                     #   (cp .env.local.example .env first — see docs/onboarding)
#   ./run.sh dev      # Start with H2 in-memory database (base application.yaml)
#   ./run.sh prod     # Start with PostgreSQL (production)
# ============================================================

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Load .env file if it exists
if [ -f ".env" ]; then
    echo "📁 Loading environment from .env file..."
    # Export all variables from .env (handle multiline values)
    set -a
    source .env
    set +a
fi

# Override profile if argument provided
if [ -n "$1" ]; then
    export SPRING_PROFILES_ACTIVE="$1"
fi

# Default to 'prod' if no profile specified
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

# JwtService signs with HS512 using the raw bytes of JWT_SECRET. The app boots
# with any value, but token signing fails at the first login if the key is
# shorter than 64 bytes — so check it here, before starting.
JWT_SECRET_BYTES=$(printf '%s' "${JWT_SECRET:-}" | wc -c | tr -d ' ')
if [ "$JWT_SECRET_BYTES" -lt 64 ] || [[ "${JWT_SECRET:-}" == replace_me* ]]; then
    echo "❌ JWT_SECRET is missing, still the placeholder, or shorter than 64 bytes (got $JWT_SECRET_BYTES)."
    echo "   Generate one with:  openssl rand -base64 64 | tr -d '\\n'"
    echo "   and put it in .env as JWT_SECRET=<value>  (see docs/onboarding/01-local-setup.md)"
    exit 1
fi

echo ""
echo "🚀 Starting FixHomi Auth Service"
echo "   Profile: $SPRING_PROFILES_ACTIVE"
echo "   Port: ${PORT:-8080}"
if [ "$SPRING_PROFILES_ACTIVE" = "prod" ]; then
    echo "   Database: PostgreSQL @ $DATABASE_HOST"
elif [ "$SPRING_PROFILES_ACTIVE" = "local" ]; then
    echo "   Database: H2 (file: ./.localdb)"
else
    echo "   Database: H2 (in-memory)"
fi
echo ""

# Run the application with Spring profile
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="$SPRING_PROFILES_ACTIVE"
