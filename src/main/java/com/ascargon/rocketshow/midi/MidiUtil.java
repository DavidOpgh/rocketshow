package com.ascargon.rocketshow.midi;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;

import org.apache.log4j.Logger;

public class MidiUtil {

    private final static Logger logger = Logger.getLogger(MidiUtil.class);

    public enum MidiDirection {
        IN, OUT
    }

    private static boolean isDeviceAllowed(String name) {
        return !(name.equals("Real Time Sequencer") || name.equals("Gervill"));
    }

    private static boolean midiDeviceHasDirection(MidiDevice midiDevice, MidiDirection midiDirection) {
        if (!isDeviceAllowed(midiDevice.getDeviceInfo().getName())) {
            return false;
        }

        return ((midiDirection == MidiDirection.IN && midiDevice.getMaxTransmitters() != 0)
                || (midiDirection == MidiDirection.OUT && midiDevice.getMaxReceivers() != 0));
    }

    public static MidiDevice getHardwareMidiDevice(com.ascargon.rocketshow.midi.MidiDevice midiDevice,
                                                   MidiDirection midiDirection) throws MidiUnavailableException {

        // Get a hardware device for a given MIDI device
        MidiDevice.Info[] midiDeviceInfos = MidiSystem.getMidiDeviceInfo();

        if (midiDevice != null) {
            // Search for a device with same id and name
            if (midiDeviceInfos.length > midiDevice.getId()) {
                if (midiDeviceInfos[midiDevice.getId()].getName().equals(midiDevice.getName())) {
                    MidiDevice hardwareMidiDevice = MidiSystem.getMidiDevice(midiDeviceInfos[midiDevice.getId()]);

                    if (midiDeviceHasDirection(hardwareMidiDevice, midiDirection)) {
                        logger.debug("Found MIDI device with same ID and name");
                        return hardwareMidiDevice;
                    }
                }
            }

            // Search for a device with the same name
            for (MidiDevice.Info midiDeviceInfo : midiDeviceInfos) {
                if (midiDeviceInfo.getName().equals(midiDevice.getName())) {
                    MidiDevice hardwareMidiDevice = MidiSystem.getMidiDevice(midiDeviceInfo);

                    if (midiDeviceHasDirection(hardwareMidiDevice, midiDirection)) {
                        logger.debug("Found MIDI device with same name");
                        return hardwareMidiDevice;
                    }
                }
            }

            // Search for a device with the same id
            if (midiDeviceInfos.length > midiDevice.getId()) {
                MidiDevice hardwareMidiDevice = MidiSystem.getMidiDevice(midiDeviceInfos[midiDevice.getId()]);

                if (midiDeviceHasDirection(hardwareMidiDevice, midiDirection)) {
                    logger.debug("Found MIDI device with same ID");
                    return hardwareMidiDevice;
                }
            }
        }

        // Return the default device, if no device has been found or no settings
        // have been specified
        logger.trace(
                "No settings provided or no device found for the provided settings. Return default MIDI device, if available");

        for (MidiDevice.Info midiDeviceInfo : midiDeviceInfos) {
            MidiDevice hardwareMidiDevice = MidiSystem.getMidiDevice(midiDeviceInfo);

            if (midiDeviceHasDirection(hardwareMidiDevice, midiDirection)) {
                logger.debug("Default MIDI device found");
                return hardwareMidiDevice;
            }
        }

        logger.trace("No MIDI device found");
        return null;
    }

    public static List<com.ascargon.rocketshow.midi.MidiDevice> getMidiDevices(MidiDirection midiDirection)
            throws MidiUnavailableException {

        // Get all available MIDI devices
        MidiDevice.Info[] midiDeviceInfos = MidiSystem.getMidiDeviceInfo();
        List<com.ascargon.rocketshow.midi.MidiDevice> midiDeviceList = new ArrayList<>();

        for (int i = 0; i < midiDeviceInfos.length; i++) {
            MidiDevice hardwareMidiDevice = MidiSystem.getMidiDevice(midiDeviceInfos[i]);

            // Filter the direction and hide system devices
            if (midiDeviceHasDirection(hardwareMidiDevice, midiDirection)) {
                com.ascargon.rocketshow.midi.MidiDevice midiDevice = new com.ascargon.rocketshow.midi.MidiDevice();
                midiDevice.setId(i);
                midiDevice.setName(midiDeviceInfos[i].getName());
                midiDevice.setVendor(midiDeviceInfos[i].getVendor());
                midiDevice.setDescription(midiDeviceInfos[i].getDescription());
                midiDeviceList.add(midiDevice);
            }
        }

        return midiDeviceList;
    }

}
