package com.ascargon.rocketshow;

import java.util.List;

import javax.sound.midi.MidiUnavailableException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.dmx.Midi2DmxMapping;
import com.ascargon.rocketshow.midi.Midi2ActionMapping;
import com.ascargon.rocketshow.midi.MidiDevice;
import com.ascargon.rocketshow.midi.MidiUtil;
import com.ascargon.rocketshow.midi.MidiUtil.MidiDirection;

@XmlRootElement
public class Settings {

	final static Logger logger = Logger.getLogger(Settings.class);

	private String defaultImagePath;

	private boolean liveDmx;

	private MidiDevice midiInDevice;
	private MidiDevice midiOutDevice;

	private List<RemoteDevice> remoteDeviceList;
	
	private Midi2ActionMapping liveMidi2ActionMapping;

	private Midi2DmxMapping fileMidi2DmxMapping;
	private Midi2DmxMapping liveMidi2DmxMapping;

	private int dmxSendDelayMillis;

	public Settings() {
		// Initialize default settings
		defaultImagePath = null;

		liveDmx = false;

		// File MIDI to DMX mapping
		fileMidi2DmxMapping = new Midi2DmxMapping();

		// Live MIDI to DMX mapping
		liveMidi2DmxMapping = new Midi2DmxMapping();

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
	
	@XmlTransient
	public RemoteDevice getRemoteDeviceById(int id) {
		for(RemoteDevice remoteDevice : remoteDeviceList) {
			if(remoteDevice.getId() == id) {
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
	public boolean isLiveDmx() {
		return liveDmx;
	}

	public void setLiveDmx(boolean liveDmx) {
		this.liveDmx = liveDmx;
	}

	@XmlElement
	public Midi2DmxMapping getFileMidi2DmxMapping() {
		return fileMidi2DmxMapping;
	}

	public void setFileMidi2DmxMapping(Midi2DmxMapping fileMidi2DmxMapping) {
		this.fileMidi2DmxMapping = fileMidi2DmxMapping;
	}

	@XmlElement
	public Midi2DmxMapping getLiveMidi2DmxMapping() {
		return liveMidi2DmxMapping;
	}

	public void setLiveMidi2DmxMapping(Midi2DmxMapping liveMidi2DmxMapping) {
		this.liveMidi2DmxMapping = liveMidi2DmxMapping;
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

	@XmlElement
	public List<RemoteDevice> getRemoteDeviceList() {
		return remoteDeviceList;
	}

	public void setRemoteDeviceList(List<RemoteDevice> remoteDeviceList) {
		this.remoteDeviceList = remoteDeviceList;
	}

	@XmlElement
	public Midi2ActionMapping getLiveMidi2ActionMapping() {
		return liveMidi2ActionMapping;
	}

	public void setLiveMidi2ActionMapping(Midi2ActionMapping liveMidi2ActionMapping) {
		this.liveMidi2ActionMapping = liveMidi2ActionMapping;
	}

}
