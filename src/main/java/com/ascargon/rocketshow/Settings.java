package com.ascargon.rocketshow;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiUnavailableException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioPlayer.PlayerType;
import com.ascargon.rocketshow.midi.MidiControl;
import com.ascargon.rocketshow.midi.MidiDevice;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiRouting;
import com.ascargon.rocketshow.midi.MidiUtil;
import com.ascargon.rocketshow.midi.MidiUtil.MidiDirection;
import com.ascargon.rocketshow.util.ShellManager;

@XmlRootElement
public class Settings {

	final static Logger logger = Logger.getLogger(Settings.class);

	// Create an own logging enum to save it in the settings xml
	public enum LoggingLevel {
		ERROR, WARN, INFO, DEBUG, TRACE
	}

	public enum AudioOutput {
		HEADPHONES, HDMI, DEVICE
	}

	private MidiDevice midiInDevice;
	private MidiDevice midiOutDevice;

	private List<RemoteDevice> remoteDeviceList = new ArrayList<RemoteDevice>();

	private List<MidiControl> midiControlList = new ArrayList<MidiControl>();
	private MidiMapping midiMapping;

	private int dmxSendDelayMillis;

	// Global play offset on file types
	private int offsetMillisMidi;
	private int offsetMillisAudio;
	private int offsetMillisVideo;

	private List<MidiRouting> deviceInMidiRoutingList = new ArrayList<MidiRouting>();
	private List<MidiRouting> remoteMidiRoutingList = new ArrayList<MidiRouting>();

	private PlayerType audioPlayerType;

	private String defaultComposition;

	private LoggingLevel loggingLevel;

	private String language = "en";

	private String deviceName;

	private boolean resetUsbAfterBoot = false;

	private AudioOutput audioOutput;

	private int audioRate;

	private AudioDevice audioDevice;

	private List<AudioBus> audioBusList = new ArrayList<AudioBus>();

	public Settings() {
		// Initialize default settings

		midiInDevice = new MidiDevice();
		midiOutDevice = new MidiDevice();

		audioPlayerType = PlayerType.MPLAYER;

		// Global MIDI mapping
		midiMapping = new MidiMapping();

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

		offsetMillisMidi = 0;
		offsetMillisAudio = 0;
		offsetMillisVideo = 0;

		audioOutput = AudioOutput.HEADPHONES;
		audioRate = 44100 /* or 48000 */;

		loggingLevel = LoggingLevel.INFO;

		updateSystem();
	}

	public RemoteDevice getRemoteDeviceByName(String name) {
		for (RemoteDevice remoteDevice : remoteDeviceList) {
			if (remoteDevice.getName().equals(name)) {
				return remoteDevice;
			}
		}

		return null;
	}

	private void setSystemAudioOutput(int id) throws Exception {
		ShellManager shellManager = new ShellManager(new String[] { "amixer", "cset", "numid=3", String.valueOf(id) });
		shellManager.getProcess().waitFor();
	}

	private int getTotalAudioChannels() {
		int total = 0;

		for (AudioBus audioBus : audioBusList) {
			total += audioBus.getChannels();
		}

		return total;
	}
	
	private String getBusNameFromId(int id) {
		return "bus" + (id + 1);
	}

	public String getAlsaDeviceFromOutputBus(String outputBus) {
		logger.debug("Find ALSA device for bus name '" + outputBus + "'...");
		
		// Get an alsa device name from a bus name
		for (int i = 0; i < audioBusList.size(); i++) {
			AudioBus audioBus = audioBusList.get(i);
			
			logger.debug("Got bus '" + audioBus.getName() + "'");
			
			if(outputBus != null && outputBus.equals(audioBus.getName())) {
				logger.debug("Found device '" + getBusNameFromId(i) + "'");
				
				return getBusNameFromId(i);
			}
		}
		
		// Return a default bus, if none is found
		if(audioBusList.size() > 0) {
			return getBusNameFromId(0);
		}
		
		return "";
	}
	
	private String getAlsaSettings() {
		// Generate the ALSA settings
		String settings = "";
		int currentChannel = 0;
		
		if(audioDevice == null) {
			// We got no audio device
			return "";
		}

		// Build the general device settings
		// @formatter:off
		settings += "pcm_slave.card_slave {\n" +
				"  pcm \"hw:" + audioDevice.getKey() + "\"\n" +
				"  channels " + getTotalAudioChannels() + "\n" +
				"\n" +
				"  rate " + audioRate + "\n" +
				"}\n";
		
		// List each bus
		for (int i = 0; i < audioBusList.size(); i++) {
			AudioBus audioBus = audioBusList.get(i);
			
			settings += "\n" +
					"pcm." + getBusNameFromId(i) + "_dshare {\n" +
					"  type dshare\n" +
					"  ipc_key " + 87273 + "\n" +
					"  slave card_slave\n";
			
			// Add each channel to the bus
			for (int j = 0; j < audioBus.getChannels(); j++) {
				settings += "  bindings." + j + " " + currentChannel + "\n";
				
				currentChannel ++;
			}
					
			settings += 	"}\n";	
			
			// Add the plugin for the bus
			settings += "\n" +
					"pcm." + getBusNameFromId(i) + " {\n" +
					"  type plug\n" +
					"  slave.pcm " + getBusNameFromId(i) + "_dshare\n" +
					"}\n";
		}
		// @formatter:on

		return settings;
	}

	private void updateAudioSystem() throws Exception {
		if (audioOutput == AudioOutput.HEADPHONES) {
			setSystemAudioOutput(1);
		} else if (audioOutput == AudioOutput.HDMI) {
			setSystemAudioOutput(2);
		} else if (audioOutput == AudioOutput.DEVICE) {
			// Write the audio settings to /home/.asoundrc and use ALSA to
			// output audio on the selected device name
			File alsaSettings = new File("/home/rocketshow/.asoundrc");
			
			try {
			    FileWriter fileWriter = new FileWriter(alsaSettings, false);
			    fileWriter.write(getAlsaSettings());
			    fileWriter.close();
			} catch (IOException e) {
			    logger.error("Could not write .asoundrc", e);
			} 
		}
	}
	
	private void updateLoggingLevel() {
		// Set the proper logging level (map from the log4j enum to our own
		// enum)
		switch (loggingLevel) {
		case INFO:
			LogManager.getRootLogger().setLevel(Level.INFO);
			break;
		case WARN:
			LogManager.getRootLogger().setLevel(Level.WARN);
			break;
		case ERROR:
			LogManager.getRootLogger().setLevel(Level.ERROR);
			break;
		case DEBUG:
			LogManager.getRootLogger().setLevel(Level.DEBUG);
			break;
		case TRACE:
			LogManager.getRootLogger().setLevel(Level.TRACE);
			break;
		}
	}

	public void updateSystem() {
		// Update all system settings

		try {
			updateAudioSystem();
		} catch (Exception e) {
			logger.error("Could not update the audio system settings", e);
		}
		
		try {
			updateLoggingLevel();
		} catch (Exception e) {
			logger.error("Could not update the logging level system settings", e);
		}

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
	public MidiMapping getMidiMapping() {
		return midiMapping;
	}

	public void setMidiMapping(MidiMapping midiMapping) {
		this.midiMapping = midiMapping;
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

	@XmlElement(name = "midiControl")
	@XmlElementWrapper(name = "midiControlList")
	public List<MidiControl> getMidiControlList() {
		return midiControlList;
	}

	public void setActionMappingList(List<MidiControl> midiControlList) {
		this.midiControlList = midiControlList;
	}

	@XmlElement
	public PlayerType getAudioPlayerType() {
		return audioPlayerType;
	}

	public void setAudioPlayerType(PlayerType audioPlayerType) {
		this.audioPlayerType = audioPlayerType;
	}

	@XmlElement
	public String getDefaultComposition() {
		return defaultComposition;
	}

	public void setDefaultComposition(String defaultComposition) {
		this.defaultComposition = defaultComposition;
	}

	@XmlElement
	public int getOffsetMillisMidi() {
		return offsetMillisMidi;
	}

	public void setOffsetMillisMidi(int offsetMillisMidi) {
		this.offsetMillisMidi = offsetMillisMidi;
	}

	@XmlElement
	public int getOffsetMillisAudio() {
		return offsetMillisAudio;
	}

	public void setOffsetMillisAudio(int offsetMillisAudio) {
		this.offsetMillisAudio = offsetMillisAudio;
	}

	@XmlElement
	public int getOffsetMillisVideo() {
		return offsetMillisVideo;
	}

	public void setOffsetMillisVideo(int offsetMillisVideo) {
		this.offsetMillisVideo = offsetMillisVideo;
	}

	@XmlElement
	public LoggingLevel getLoggingLevel() {
		return loggingLevel;
	}

	public void setLoggingLevel(LoggingLevel loggingLevel) {
		this.loggingLevel = loggingLevel;
	}

	@XmlElement
	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	@XmlElement
	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	@XmlElement
	public boolean isResetUsbAfterBoot() {
		return resetUsbAfterBoot;
	}

	public void setResetUsbAfterBoot(boolean resetUsbAfterBoot) {
		this.resetUsbAfterBoot = resetUsbAfterBoot;
	}

	@XmlElement
	public AudioOutput getAudioOutput() {
		return audioOutput;
	}

	public void setAudioOutput(AudioOutput audioOutput) {
		this.audioOutput = audioOutput;
	}

	@XmlElement(name = "audioBus")
	@XmlElementWrapper(name = "audioBusList")
	public List<AudioBus> getAudioBusList() {
		return audioBusList;
	}

	public void setAudioBusList(List<AudioBus> audioBusList) {
		this.audioBusList = audioBusList;
	}

	public int getAudioRate() {
		return audioRate;
	}

	@XmlElement
	public void setAudioRate(int audioRate) {
		this.audioRate = audioRate;
	}

	@XmlElement
	public AudioDevice getAudioDevice() {
		return audioDevice;
	}

	public void setAudioDevice(AudioDevice audioDevice) {
		this.audioDevice = audioDevice;
	}

}
