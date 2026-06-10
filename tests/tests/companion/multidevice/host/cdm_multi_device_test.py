#!/usr/bin/env python3
# Lint as: python3
"""
Test core CDM APIs involving multiple devices on mobly.
Run: atest CtsCompanionDeviceManagerMultiDeviceTestCases
"""

import api_flags_utils
import cdm_base_test

from android.platform.test.annotations import ApiTest, CddTest
from mobly import asserts
from mobly import test_runner
from test_utils import wait
from time import sleep

@CddTest(requirements = ["3.16/C-1-1", "3.16/C-1-2"])
class CompanionDeviceManagerTestClass(cdm_base_test.BaseTestClass):

    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#associate(android.companion.AssociationRequest, android.companion.CompanionDeviceManager.Callback, android.os.Handler)',
            'android.companion.CompanionDeviceManager#getMyAssociations()'
    ])
    def test_associate_createsAssociation_classicBluetooth(self):
        """Test that CDM can create association with another BT device"""

        # Skip if device is a watch
        asserts.skip_if(self.primary.cdm.isWatch(), 'Cannot create association as a watch.')

        # Create association
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)

        # Assert association was created
        associations = self.primary.cdm.getMyAssociations()
        asserts.assert_true(secondary_id in associations, 'Association not found.')


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#buildPermissionTransferUserConsentIntent(int)',
            'android.companion.CompanionDeviceManager#attachSystemDataTransport(int, java.io.InputStream, java.io.OutputStream)',
            'android.companion.CompanionDeviceManager#detachSystemDataTransport(int)',
            'android.companion.CompanionDeviceManager#startSystemDataTransfer(int, java.util.concurrent.Executor, android.os.OutcomeReceiver)'
    ])
    def test_permissions_sync(self):
        """Test that CDM can perform permissions sync from one device to another via BT"""

        # Skip if either device is a watch
        asserts.skip_if(self.primary.cdm.isWatch(), 'Cannot create association as a watch.')
        asserts.skip_if(self.secondary.cdm.isWatch(), 'Cannot create association as a watch.')

        # Assume both devices are on same build type (debug vs user)
        primary_debuggable = self.primary.build_info['debuggable'] == '1'
        secondary_debuggable = self.secondary.build_info['debuggable'] == '1'
        asserts.skip_if(primary_debuggable != secondary_debuggable, 'Both devices must be on the same type of build')

        # If on user build, assume AVF compliance for peer profiles
        if not primary_debuggable:
            primary_attestation = self.primary.cdm.generateAttestation()
            secondary_attestation = self.secondary.cdm.generateAttestation()
            primary_verified = self.secondary.cdm.verifyAttestation(primary_attestation)
            secondary_verified = self.primary.cdm.verifyAttestation(secondary_attestation)
            asserts.skip_if(not primary_verified, 'Secondary device failed to verify primary device')
            asserts.skip_if(not secondary_verified, 'Primary device failed to verify secondary device')

        # Create associations
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)

        self.primary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        primary_id = self.secondary.cdm.associate(self.primary.address)

        # Start permissions sync and wait for completion
        self.bt_pair_devices()
        self.secondary.cdm.attachServerSocket(primary_id)
        self.primary.cdm.attachClientSocket(secondary_id)
        self.primary.cdm.requestPermissionTransferUserConsent(secondary_id)
        self.primary.cdm.startPermissionsSync(secondary_id)


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#removeBond(int)'
    ])
    def test_removeBond_associatedDevice_succeeds(self):
        """This tests that CDM can remove bluetooth bond from an associated device."""

        # Skip if device is a watch
        asserts.skip_if(self.primary.cdm.isWatch(), 'Cannot create association as a watch.')

        # Skip if removeBond API flag is disabled
        api_flags_utils.assume_enabled(self.primary, 'unpair_associated_device')

        # Associate and assert successful pairing
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)
        self.bt_pair_devices()
        sleep(cdm_base_test.OPERATION_DELAY_TIME)
        asserts.assert_true(self.secondary.address in self.primary.paired_devices(), 'Pairing unsuccessful.')

        # Remove BT pairing via CDM and assert success
        asserts.assert_true(self.primary.cdm.removeBond(secondary_id), "Unpairing failed.")
        sleep(cdm_base_test.OPERATION_DELAY_TIME)
        asserts.assert_false(self.secondary.address in self.primary.paired_devices(), 'Devices should not be paired.')


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#associate(android.companion.AssociationRequest, android.companion.CompanionDeviceManager.Callback, android.os.Handler)',
    ])
    def test_association_persistence_after_reboot(self):
        """Test that association persists after a device reboot"""

        # Skip if device is a watch
        asserts.skip_if(self.primary.cdm.isWatch(), 'Cannot create association as a watch.')

        # Create association
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)

        # Reboot the primary device
        with (self.primary.handle_reboot()):
            self.primary.reboot()

        # The association should be remaining.
        associations = self.primary.cdm.getMyAssociations()
        asserts.assert_true(secondary_id in associations, 'Association not found.')


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#startObservingDevicePresence(android.companion.ObservingDevicePresenceRequest)',
            'android.companion.CompanionDeviceManager#stopObservingDevicePresence(android.companion.ObservingDevicePresenceRequest)'
    ])
    def test_startObservingDevicePresence_observesEvents_bt_classic(self):
        """
        This tests that CDM can listen for BT classic device presence events from
        associated devices.
        """

        # Skip if either device is a watch
        asserts.skip_if(self.primary.cdm.isWatch(), 'Cannot create association as a watch.')
        asserts.skip_if(self.secondary.cdm.isWatch(), 'Cannot create association as a watch.')

        # Skip if device presence API flag is disabled
        api_flags_utils.assume_enabled(self.primary, 'device_presence')

        # Associate and start observing
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)
        self.primary.cdm.startObservingDevicePresence(secondary_id)

        # Assert classic bluetooth pairing is detected
        self.bt_pair_devices()
        connected = wait(lambda: self.primary.cdm.isAssociationBtConnected(secondary_id))
        asserts.assert_true(connected, 'Device appearance was not observed.')

        # Assert bluetooth unpair is detected
        self.bt_unpair_devices()
        gone = wait(lambda: not self.primary.cdm.isAssociationBtConnected(secondary_id))
        asserts.assert_true(gone, 'Device disappearance was not observed.')

        # Stop observing device presence
        self.primary.cdm.stopObservingDevicePresence(secondary_id)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()
