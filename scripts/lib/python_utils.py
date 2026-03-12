#!/usr/bin/env python3
"""
Shared utilities for Python test scripts in IronBucket.

Provides: logging, JSON writing, environment resolution, CLI parsing.
"""

import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional


class Colors:
    """ANSI color codes for terminal output."""
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    MAGENTA = '\033[0;35m'
    CYAN = '\033[0;36m'
    GRAY = '\033[0;37m'
    BOLD = '\033[1m'
    NC = '\033[0m'  # No Color


class Logger:
    """Unified logging for Python scripts."""
    
    def __init__(self, log_file: Optional[str] = None, verbose: bool = False):
        self.log_file = log_file
        self.verbose = verbose
        if log_file:
            Path(log_file).parent.mkdir(parents=True, exist_ok=True)
    
    def _write(self, level: str, color: str, message: str, force_console: bool = False):
        """Write to console and optionally log file."""
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        formatted = f"{color}[{level}]{Colors.NC} {timestamp} {message}"
        
        print(formatted, file=sys.stdout if force_console else sys.stderr if level == 'ERROR' else sys.stdout)
        
        if self.log_file:
            # Write to log file without colors
            plain = f"[{level}] {timestamp} {message}"
            with open(self.log_file, 'a') as f:
                f.write(plain + '\n')
    
    def info(self, message: str):
        """Info level."""
        if self.verbose:
            self._write('INFO', Colors.BLUE, message)
    
    def success(self, message: str):
        """Success level."""
        self._write('✅ SUCCESS', Colors.GREEN, message)
    
    def warn(self, message: str):
        """Warning level."""
        self._write('⚠️  WARN', Colors.YELLOW, message)
    
    def error(self, message: str):
        """Error level."""
        self._write('❌ ERROR', Colors.RED, message, force_console=True)
    
    def header(self, message: str):
        """Print formatted header."""
        print("")
        print(f"{Colors.BLUE}╔════════════════════════════════════════════════════════════════╗{Colors.NC}")
        print(f"{Colors.BLUE}║{Colors.NC}{message}{Colors.BLUE}║{Colors.NC}")
        print(f"{Colors.BLUE}╚════════════════════════════════════════════════════════════════╝{Colors.NC}")
        print("")
    
    def section(self, message: str):
        """Print formatted section."""
        print("")
        print(f"{Colors.MAGENTA}{'='*70}{Colors.NC}")
        print(f"{Colors.MAGENTA}  {message}{Colors.NC}")
        print(f"{Colors.MAGENTA}{'='*70}{Colors.NC}")
        print("")
    
    def step(self, message: str):
        """Print step with marker."""
        print(f"{Colors.YELLOW}▶ {message}{Colors.NC}")


class EnvResolver:
    """Resolve environment variables with sensible defaults."""
    
    @staticmethod
    def get_project_root() -> str:
        """Get PROJECT_ROOT, preferring env var over derived path."""
        if pr := os.environ.get('PROJECT_ROOT'):
            return pr
        # Try to find root by locating .env.defaults or scripts/
        cwd = Path.cwd()
        for p in [cwd] + list(cwd.parents):
            if (p / 'scripts' / '.env.defaults').exists():
                return str(p)
        return str(Path(__file__).parent.parent.parent)
    
    @staticmethod
    def get_temp_dir() -> str:
        """Get TEMP_DIR for project (not /tmp)."""
        return os.environ.get('TEMP_DIR', str(Path(EnvResolver.get_project_root()) / 'build' / 'temp'))
    
    @staticmethod
    def get_log_dir() -> str:
        """Get LOG_DIR for test execution logs."""
        return os.environ.get('LOG_DIR', str(Path(EnvResolver.get_project_root()) / 'test-results' / 'logs'))
    
    @staticmethod
    def get_report_dir() -> str:
        """Get REPORTS_DIR for test reports."""
        return os.environ.get('REPORTS_DIR', str(Path(EnvResolver.get_project_root()) / 'test-results' / 'reports'))
    
    @staticmethod
    def is_container() -> bool:
        """Check if running inside a container."""
        return os.environ.get('IS_CONTAINER', 'false').lower() == 'true' or \
               Path('/.dockerenv').exists() or \
               Path('/run/.containerenv').exists()
    
    @staticmethod
    def get_timestamp() -> str:
        """Get ISO format timestamp."""
        return datetime.now(timezone.utc).isoformat()
    
    @staticmethod
    def get_timestamp_short() -> str:
        """Get compact timestamp for filenames."""
        return datetime.now().strftime('%Y%m%d_%H%M%S')


class JSONReporter:
    """Write structured JSON reports."""
    
    @staticmethod
    def write(data: Dict[str, Any], output_path: str, pretty: bool = True) -> Path:
        """Write JSON to file with optional pretty-printing."""
        output_path = Path(output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        
        with open(output_path, 'w') as f:
            json.dump(data, f, indent=2 if pretty else None)
        
        return output_path
    
    @staticmethod
    def read(input_path: str) -> Dict[str, Any]:
        """Read JSON from file."""
        with open(input_path, 'r') as f:
            return json.load(f)


def main_with_error_handling(func):
    """Decorator for main() functions to handle exceptions gracefully."""
    def wrapper(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except KeyboardInterrupt:
            print(f"\n{Colors.YELLOW}⊘ Interrupted by user{Colors.NC}", file=sys.stderr)
            sys.exit(130)
        except Exception as e:
            print(f"{Colors.RED}❌ Fatal error: {e}{Colors.NC}", file=sys.stderr)
            import traceback
            traceback.print_exc()
            sys.exit(1)
    return wrapper


__all__ = [
    'Colors',
    'Logger',
    'EnvResolver',
    'JSONReporter',
    'main_with_error_handling',
]
