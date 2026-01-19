#!/bin/bash
# IronBucket Background Spinup with Live Monitoring
# Usage: ./spinup-background.sh [--with-mtls] [--logs]
#
# This script runs spinup.sh in the background and provides live log monitoring
# Press Ctrl+C to stop monitoring (spinup continues in background)
#
# Options:
#   --with-mtls    Start services with mTLS enabled
#   --logs         Show full logs instead of filtered view

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Parse arguments
SPINUP_ARGS=""
SHOW_FULL_LOGS=false

for arg in "$@"; do
    case $arg in
        --with-mtls)
            SPINUP_ARGS="$SPINUP_ARGS --with-mtls"
            ;;
        --logs)
            SHOW_FULL_LOGS=true
            ;;
        *)
            echo "Unknown option: $arg"
            echo "Usage: ./spinup-background.sh [--with-mtls] [--logs]"
            exit 1
            ;;
    esac
done

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║       IronBucket - Background Spinup with Monitoring           ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Get the log file path
source .env.defaults 2>/dev/null || true
LOG_FILE="${LOG_FILE:-/workspaces/IronBucket/test-results/logs/script-execution.log}"
PID_FILE="/tmp/ironbucket-spinup.pid"

# Check if spinup is already running
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  Spinup is already running (PID: $OLD_PID)${NC}"
        echo ""
        echo "Options:"
        echo "  1. Monitor existing run:   tail -f $LOG_FILE"
        echo "  2. Kill and restart:       kill $OLD_PID && rm $PID_FILE"
        echo "  3. Check Docker services:  docker ps"
        echo ""
        exit 1
    else
        # Stale PID file
        rm "$PID_FILE"
    fi
fi

# Start spinup in background
echo -e "${CYAN}▶ Starting spinup.sh in background$SPINUP_ARGS...${NC}"
nohup bash "$SCRIPT_DIR/spinup.sh" $SPINUP_ARGS > /dev/null 2>&1 &
SPINUP_PID=$!
echo $SPINUP_PID > "$PID_FILE"

echo -e "${GREEN}✅ Spinup started (PID: $SPINUP_PID)${NC}"
echo ""
echo -e "${CYAN}Monitoring options:${NC}"
echo "  • Live logs:       tail -f $LOG_FILE"
echo "  • Docker status:   docker ps"
echo "  • Stop monitoring: Ctrl+C (spinup continues)"
echo "  • Kill spinup:     kill $SPINUP_PID"
echo ""
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${CYAN}Live Log Monitor (Ctrl+C to stop monitoring)${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Wait a second for the log file to be created
sleep 2

# Follow the log file with optional filtering
if [ "$SHOW_FULL_LOGS" = true ]; then
    # Show full logs
    tail -f "$LOG_FILE" 2>/dev/null &
    TAIL_PID=$!
else
    # Show filtered logs (important events only)
    tail -f "$LOG_FILE" 2>/dev/null | grep -E "(SUCCESS|ERROR|FAILED|Testing|Starting|mTLS|Docker|E2E|Alice|Bob|▶|✅|❌)" --line-buffered &
    TAIL_PID=$!
fi

# Trap Ctrl+C to clean up tail but keep spinup running
trap "kill $TAIL_PID 2>/dev/null; echo ''; echo -e '${GREEN}Monitoring stopped. Spinup continues in background (PID: $SPINUP_PID)${NC}'; echo ''; exit 0" INT

# Wait for tail to finish (it won't unless killed)
wait $TAIL_PID

# Clean up PID file when spinup finishes
wait $SPINUP_PID 2>/dev/null
rm -f "$PID_FILE"
echo ""
echo -e "${GREEN}✅ Spinup completed!${NC}"
