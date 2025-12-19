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
    # Export all variables from .env
    export $(grep -v '^#' .env | grep -v '^\s*$' | xargs)
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
echo "   Database URL: ${DATABASE_URL:0:50}..."
echo ""

# Run the application with Spring profile
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="$SPRING_PROFILES_ACTIVE"
