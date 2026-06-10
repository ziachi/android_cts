# Copyright 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Utility functions for gen2 rig hardware."""


import logging
import struct
import time
import pyudev
import serial


# baudrates used for lights and servo controllers
_ARDUINO_BAUDRATE = 9600  # baudrate as set in firmware
_ROTATOR_BAUDRATE = 115200  # baudrate as set in firmware
_MAX_CHANNEL_ID = 5

# servo controller commands
_LSS_COMMAND_START = '#'
_LSS_COMMAND_END = '\r'
_LSS_CONFIG_MAX_SPEED_RPM = 'CSR'
_LSS_CONFIG_ANGULAR_STIFFNESS = 'CAS'
_LSS_CONFIG_ANGULAR_HOLDING_STIFFNESS = 'CAH'
_LSS_CONFIG_ANGULAR_ACCELERATION = 'CAA'
_LSS_CONFIG_ANGULAR_DECELERATION = 'CAD'
_LSS_ACTION_MOVE = 'D'
_LSS_ACTION_HOLD = 'H'

# servo controller configuration
_DEFAULT_MAX_SPEED_RPM = 45
_DEFAULT_ANGULAR_STIFFNESS = 0
_DEFAULT_ANGULAR_HOLDING_STIFFNESS = 0
_DEFAULT_ANGULAR_ACCELERATION = 35
_DEFAULT_ANGULAR_DECELERATION = 20

# Position of origin.
_POSITION_0_DEGREE = '0'
_SERVO_ANGLE_SCALE_FACTOR = 10
_MIN_SERVO_POSITION = -180
_MAX_SERVO_POSITION = 180

_ARDUINO_BRIGHTNESS_MAX = 1
_ARDUINO_BRIGHTNESS_MIN = 0
_ARDUINO_LIGHT_START_BYTE = 100
_ARDUINO_CMD_LENGTH = 3
_WAIT_FOR_ROTATOR_MOVEMENT = 2
_WAIT_FOR_CMD_COMPLETION = 1

# Constants for strings used to find serial port
_ARDUINO_STR = 'Arduino'
_LIGHTS_STR = 'lights'
_ROTATOR_STR = 'rotator'
_CH340_STR = 'CH340'
_MEGA_STR = 'Mega'


def _check_channel(channel):
  """Checks if the channel used is a valid number or not.

  Args:
    channel: int; channel id used in config file
  Raises:
    ValueError if the channel id is not valid
  """
  if not (channel <= _MAX_CHANNEL_ID):
    raise ValueError('Channel id is not valid.')


def _rotator_write(serial_port, channel, command, value=None):
  """Writes command to the rotator board.

  Args:
    serial_port: serial port to be used for communication
    channel: int; channel id for rotator
    command: List of bytes; the command send to the rotator board.
    value: Integer; the parameter value send to the rotator board.
  """
  tmp = f'{channel}{command}'
  if value:
    tmp += str(value)
    msg = (f'{_LSS_COMMAND_START}{tmp}{_LSS_COMMAND_END}').encode()
    logging.debug('Writing message to rotator board: %s', msg)
    serial_port.write(msg)


def find_serial_port(name):
  """Determine the serial port for gen2 rig controllers and open.

  serial port details: udevadm info -q property --name=<port-name>

  Args:
    name: str; string of device to locate (ie. 'gen2_motor', 'gen2_lights')
  Returns:
    serial port object
  """
  port_name = None
  devices = pyudev.Context()
  for device in devices.list_devices(subsystem='tty', ID_BUS='usb'):
    logging.debug('Checking device: %s', device)
    if _LIGHTS_STR in name:
      logging.debug('Finding serial port for lights')
      if _ARDUINO_STR in device['ID_VENDOR_FROM_DATABASE']:
        if _MEGA_STR in  device['ID_MODEL_FROM_DATABASE']:
          port_name = device['DEVNAME']
          logging.debug('Lighting controller port_name: %s', port_name)
          return serial.Serial(port_name, _ARDUINO_BAUDRATE, timeout=1)

    if _ROTATOR_STR in name:
      logging.debug('Finding serial port for rotator')
      if _CH340_STR in device['ID_MODEL_FROM_DATABASE']:
        port_name = device['DEVNAME']
        logging.debug('Rotator controller port_name: %s', port_name)
        return serial.Serial(port_name, _ROTATOR_BAUDRATE, timeout=1)

  if port_name is None:
    logging.debug('Failed to find the serial port.')
    return None


def configure_rotator(serial_port, channel):
  """Configure rotator with default settings.

  Args:
    serial_port: serial port to be used for communication
    channel: int; channel used by rotator
  """
  _check_channel(channel)
  _set_max_speed_rpm(serial_port, channel, _DEFAULT_MAX_SPEED_RPM)
  _set_angular_stiffness(serial_port, channel, _DEFAULT_ANGULAR_STIFFNESS)
  _set_angular_holding_stiffness(serial_port, channel,
                                 _DEFAULT_ANGULAR_HOLDING_STIFFNESS)
  _set_angular_acceleration(serial_port, channel, _DEFAULT_ANGULAR_ACCELERATION)
  _set_angular_deceleration(serial_port, channel, _DEFAULT_ANGULAR_DECELERATION)


def _set_max_speed_rpm(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_MAX_SPEED_RPM, value)


def _set_angular_stiffness(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_ANGULAR_STIFFNESS, value)


def _set_angular_holding_stiffness(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_ANGULAR_HOLDING_STIFFNESS,
                 value)


def _set_angular_acceleration(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_ANGULAR_ACCELERATION, value)


def _set_angular_deceleration(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_ANGULAR_DECELERATION, value)


def _move_to(serial_port, channel, position):
  _rotator_write(serial_port, channel, _LSS_ACTION_MOVE, position)
  # Wait for two seconds.
  time.sleep(_WAIT_FOR_ROTATOR_MOVEMENT)


def rotate(serial_port, channel, position_degree=0):
  """Rotate servo to the specified direction.

  Args:
    serial_port: serial port to be used for communication
    channel: int; channel used by rotator
    position_degree: float; Position in degrees to move the servo
      Default position is set to 0 degrees which is the center position.
      A full circle is from -180 to 180 degrees.
      Positive value will move the servo in clockwise direction.
      Negative value will move the servo  in anti-clockwise direction.

  Returns:
    Command response.
  """
  if _MIN_SERVO_POSITION <= position_degree <= _MAX_SERVO_POSITION:
    if position_degree != 0:
      position_degree = position_degree * _SERVO_ANGLE_SCALE_FACTOR
      position = str(position_degree)
    else:
      position = _POSITION_0_DEGREE
    logging.debug('Moving servo %s to position %s', channel, position)
    _move_to(serial_port, channel, position)
    response = f'Moving servo {channel} to direction {position_degree}'
    # Hold the angular position after movement
    _rotator_write(serial_port, channel, _LSS_ACTION_HOLD)
    return response
  else:
    logging.debug('Not a valid servo position: %s', position_degree)
    return None


def set_light_brightness(serial_port, channel, brightness_level, delay=0):
  """Set the light to specified brightness.

  Args:
    serial_port: object; serial port
    channel: str for lighting channel
    brightness_level: int value of brightness.
    delay: int; time in seconds
  """
  def to_char(digit):
    return digit + ord('0')

  cmd = [struct.pack('B', i) for i in [
      _ARDUINO_LIGHT_START_BYTE, to_char(channel), to_char(brightness_level)]]
  logging.debug('Lighting cmd: %s', cmd)
  for item in cmd:
    serial_port.write(item)
  time.sleep(delay)


def set_lighting_state(serial_port, channel, state):
  """Control the lights in gen2 rig.

  Args:
    serial_port: serial port object
    channel: str for lighting channel
    state: str 'ON/OFF'
  """
  logging.debug('Setting the lights state to: %s', state)
  if state == 'ON':
    level = 1
  elif state == 'OFF':
    level = 0
  else:
    raise ValueError(f'Lighting state not defined correctly: {state}')
  set_light_brightness(serial_port, channel, level,
                       delay=_WAIT_FOR_CMD_COMPLETION)

