#!/usr/bin/env python3
# Lint as: python3
"""
Utility class for various test usage.
"""

import time

from collections.abc import Callable

WAIT_DEFAULT_TIMEOUT = 5
WAIT_DEFAULT_POLLING_INTERVAL = 0.2

def wait(condition: Callable[[], bool], timeout: int = WAIT_DEFAULT_TIMEOUT, interval: int = WAIT_DEFAULT_POLLING_INTERVAL) -> bool:
    """
    Wait until condition becomes true before timing out.
    Return true if condition is met, and false otherwise.
    """
    start_time = time.time()
    while not condition():
        elapsed_time = time.time() - start_time
        if elapsed_time >= timeout:
            return False
        time.sleep(interval)
    return True
