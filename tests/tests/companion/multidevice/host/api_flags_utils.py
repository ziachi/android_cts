#!/usr/bin/env python3
# Lint as: python3
"""
Utility class for checking CDM API flags on test devices.
"""

from mobly import asserts
from mobly.controllers import android_device
from mobly.tools import device_flags

NAMESPACE = 'companion'
PACKAGE_NAME = 'android.companion'

def assume_enabled(ad: android_device.AndroidDevice, flag_name: str):
    """Assume that a CDM API flag is enabled on the android device.

    If the device is either missing the flag or the flag is disabled, then skip this test.
    The flag name must omit the flag namespace and package name.

    Args:
        ad: android device controller
        flag_name: name of the API flag to assume enabled.
    """
    flags = device_flags.DeviceFlags(ad)
    enabled = flags.get_value(NAMESPACE, f'{PACKAGE_NAME}.{flag_name}')
    asserts.skip_if(not enabled, f'{flag_name} must be enabled for this test.')
