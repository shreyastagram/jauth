#!/bin/bash
# ============================================================
# FixHomi Auth Service - Startup Script
# ============================================================
# This script loads environment variables from .env file
# and starts the Spring Boot application with the configured profile.
#
# Usage:
#   ./run.sh          # Start with profile from .env (default: prod)
#   ./run.sh dev      # Start with H2 database (development)
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

echo ""
echo "🚀 Starting FixHomi Auth Service"
echo "   Profile: $SPRING_PROFILES_ACTIVE"
echo "   Port: ${PORT:-8080}"
if [ "$SPRING_PROFILES_ACTIVE" = "prod" ]; then
    echo "   Database: PostgreSQL @ $DATABASE_HOST"
else
    echo "   Database: H2 (in-memory)"
fi
echo ""

# Run the application with Spring profile
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="$SPRING_PROFILES_ACTIVE"
