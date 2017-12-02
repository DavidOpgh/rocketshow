package com.ascargon.rocketshow;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiUnavailableException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.audio.AudioPlayer.PlayerType;
import com.ascargon.rocketshow.dmx.Midi2DmxMapping;
import com.ascargon.rocketshow.midi.Midi2ActionMapping;
import com.ascargon.rocketshow.midi.MidiDevice;
import com.ascargon.rocketshow.midi.MidiRouting;
import com.ascargon.rocketshow.midi.MidiUtil;
import com.ascargon.rocketshow.midi.MidiUtil.MidiDirection;

@XmlRootElement
public class Settings {

	final static Logger logger = Logger.getLogger(Settings.class);

	private String defaultImagePath;

	private MidiDevice midiInDevice;
	private MidiDevice midiOutDevice;

	private List<RemoteDevice> remoteDeviceList = new ArrayList<RemoteDevice>();

	private Midi2ActionMapping midi2ActionMapping;
	private Midi2DmxMapping midi2DmxMapping;

	private int dmxSendDelayMillis;

	private List<MidiRouting> deviceInMidiRoutingList = new ArrayList<MidiRouting>();
	private List<MidiRouting> remoteMidiRoutingList = new ArrayList<MidiRouting>();

	private PlayerType audioPlayerType;

	public Settings() {
		// Initialize default settings

		audioPlayerType = PlayerType.ALSA_PLAYER;

		defaultImagePath = null;

		// Global MIDI to action mapping
		midi2ActionMapping = new Midi2ActionMapping();

		// Global MIDI to DMX mapping
		midi2DmxMapping = new Midi2DmxMapping();

		try {
			List<MidiDevice> midiInDeviceList;
			midiInDeviceList = MidiUtil.getMidiDevices(MidiDirection.IN);
			if (midiInDeviceList.size() > 0) {
				midiInDevice = midiInDeviceList.get(0);
			}
		} catch (MidiUnavailableException e) {
			logger.error("Could not get any MIDI IN devices");
			logger.error(e.getStackTrace());
		}

		try {
			List<MidiDevice> midiOutDeviceList;
			midiOutDeviceList = MidiUtil.getMidiDevices(MidiDirection.OUT);
			if (midiOutDeviceList.size() > 0) {
				midiOutDevice = midiOutDeviceList.get(0);
			}
		} catch (MidiUnavailableException e) {
			logger.error("Could not get any MIDI OUT devices");
			logger.error(e.getStackTrace());
		}

		dmxSendDelayMillis = 10;
	}

	public RemoteDevice getRemoteDeviceById(int id) {
		for (RemoteDevice remoteDevice : remoteDeviceList) {
			if (remoteDevice.getId() == id) {
				return remoteDevice;
			}
		}

		return null;
	}

	@XmlElement
	public String getDefaultImagePath() {
		return defaultImagePath;
	}

	public void setDefaultImagePath(String defaultImagePath) {
		this.defaultImagePath = defaultImagePath;
	}

	@XmlElement
	public MidiDevice getMidiInDevice() {
		return midiInDevice;
	}

	public void setMidiInDevice(MidiDevice midiInDevice) {
		this.midiInDevice = midiInDevice;
	}

	@XmlElement
	public MidiDevice getMidiOutDevice() {
		return midiOutDevice;
	}

	public void setMidiOutDevice(MidiDevice midiOutDevice) {
		this.midiOutDevice = midiOutDevice;
	}

	@XmlElement
	public int getDmxSendDelayMillis() {
		return dmxSendDelayMillis;
	}

	public void setDmxSendDelayMillis(int dmxSendDelayMillis) {
		this.dmxSendDelayMillis = dmxSendDelayMillis;
	}

	@XmlElement(name = "remoteDevice")
	@XmlElementWrapper(name = "remoteDeviceList")
	public List<RemoteDevice> getRemoteDeviceList() {
		return remoteDeviceList;
	}

	public void setRemoteDeviceList(List<RemoteDevice> remoteDeviceList) {
		this.remoteDeviceList = remoteDeviceList;
	}

	@XmlElement
	public Midi2ActionMapping getMidi2ActionMapping() {
		return midi2ActionMapping;
	}

	public void setMidi2ActionMapping(Midi2ActionMapping midi2ActionMapping) {
		this.midi2ActionMapping = midi2ActionMapping;
	}

	@XmlElement
	public Midi2DmxMapping getMidi2DmxMapping() {
		return midi2DmxMapping;
	}

	public void setMidi2DmxMapping(Midi2DmxMapping midi2DmxMapping) {
		this.midi2DmxMapping = midi2DmxMapping;
	}

	@XmlElement(name = "deviceInMidiRouting")
	@XmlElementWrapper(name = "deviceInMidiRoutingList")
	public List<MidiRouting> getDeviceInMidiRoutingList() {
		return deviceInMidiRoutingList;
	}

	public void setDeviceInMidiRoutingList(List<MidiRouting> deviceInMidiRoutingList) {
		for (MidiRouting deviceInMidiRouting : deviceInMidiRoutingList) {
			deviceInMidiRouting.setMidiSource("input MIDI device");
		}
		this.deviceInMidiRoutingList = deviceInMidiRoutingList;
	}

	@XmlElement(name = "remoteMidiRouting")
	@XmlElementWrapper(name = "remoteMidiRoutingList")
	public List<MidiRouting> getRemoteMidiRoutingList() {
		return remoteMidiRoutingList;
	}

	public void setRemoteMidiRoutingList(List<MidiRouting> remoteMidiRoutingList) {
		for (MidiRouting remoteMidiRouting : remoteMidiRoutingList) {
			remoteMidiRouting.setMidiSource("remote MIDI");
		}
		this.remoteMidiRoutingList = remoteMidiRoutingList;
	}

	@XmlElement
	public PlayerType getAudioPlayerType() {
		return audioPlayerType;
	}

	public void setAudioPlayerType(PlayerType audioPlayerType) {
		this.audioPlayerType = audioPlayerType;
	}

}
