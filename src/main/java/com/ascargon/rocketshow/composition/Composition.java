package com.ascargon.rocketshow.composition;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiRouting;;

@XmlRootElement
public class Composition {

	final static Logger logger = Logger.getLogger(Composition.class);

	public enum PlayState {
		PLAYING, // Is the composition playing?
		PAUSED, // Is the composition paused?
		STOPPING, // Is the composition being stopped?
		STOPPED, // Is the composition stopped?
		LOADING // Is the composition waiting for all files to be loaded to
				// start
				// playing?
	}

	private PlayState playState = PlayState.STOPPED;

	private String name;

	private boolean autoStartNextComposition = false;

	private String notes;

	private long durationMillis;

	private MidiMapping midiMapping = new MidiMapping();

	private List<File> fileList = new ArrayList<File>();

	private Manager manager;

	private Timer autoStopTimer;

	private LocalDateTime lastStartTime;
	private long passedMillis;

	private boolean filesLoaded = false;

	// Is this the default composition?
	private boolean defaultComposition = false;

	public void close() throws Exception {
		for (File file : fileList) {
			file.close();
		}

		filesLoaded = false;

		// Cancel the auto-stop timer
		if (autoStopTimer != null) {
			autoStopTimer.cancel();
			autoStopTimer = null;
		}

		playState = PlayState.STOPPED;
	}

	public void playerLoaded() {
		synchronized (this) {
			notifyAll();
		}
	}

	private boolean allFilesLoaded() {
		for (File file : fileList) {
			if (file.isActive() && !file.isLoaded()) {
				return false;
			}
		}

		return true;
	}

	// Load all files but don't start playing
	public synchronized void loadFiles() throws Exception {
		if (filesLoaded) {
			return;
		}

		if (!defaultComposition) {
			manager.stopDefaultComposition();
		}

		playState = PlayState.LOADING;

		if (!defaultComposition) {
			manager.getStateManager().notifyClients();
		}

		logger.debug("Loading all files for composition '" + name + "'...");

		for (File file : fileList) {
			if (file.isActive()) {
				if (file instanceof MidiFile) {
					MidiFile midiFile = (MidiFile) file;

					for (MidiRouting midiRouting : midiFile.getMidiRoutingList()) {
						midiRouting.getMidiMapping().setParent(midiMapping);
					}
				}

				file.setManager(manager);
				file.setComposition(this);

				file.load();
			}
		}

		synchronized (this) {
			while (!allFilesLoaded()) {
				wait();
			}
		}

		logger.debug("All files for composition '" + name + "' loaded");

		// Maybe we are stopping meanwhile
		if (playState == PlayState.LOADING) {
			playState = PlayState.PAUSED;
			filesLoaded = true;
		}
	}

	private void startAutoStopTimer(long passedMillis) {
		// Start the autostop timer, to automatically stop the composition, as
		// soon as the last file (the longest one, which has the most offset)
		// has been finished)
		long maxDurationAndOffset = 0;

		for (File file : fileList) {
			if (file.isActive()) {
				int fileOffset = 0;

				if (file.isLoop()) {
					// At least one file is looped -> don't stop the composition automatically
					return;
				}

				if (file instanceof MidiFile) {
					fileOffset = ((MidiFile) file).getFullOffsetMillis();
				} else if (file instanceof AudioFile) {
					fileOffset = ((AudioFile) file).getFullOffsetMillis();
				} else if (file instanceof VideoFile) {
					fileOffset = ((VideoFile) file).getFullOffsetMillis();
				}

				if (file.getDurationMillis() + fileOffset > maxDurationAndOffset) {
					maxDurationAndOffset = file.getDurationMillis() + fileOffset;
				}
			}
		}

		maxDurationAndOffset -= passedMillis;

		logger.debug("Scheduled the auto-stop timer in " + maxDurationAndOffset + " millis");

		autoStopTimer = new Timer();
		autoStopTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					logger.debug("Automatically stopping the composition...");

					autoStopTimer.cancel();
					autoStopTimer = null;

					if (autoStartNextComposition && manager.getCurrentSet().hasNextComposition()) {
						// Stop, don't play the default composition but start
						// playing the next composition
						manager.getPlayer().stop(false);

						if (manager.getCurrentSet() != null) {
							manager.getCurrentSet().nextComposition(false);
							manager.getPlayer().play();
						}
					} else {
						// Stop, play the default composition and select the
						// next composition automatically (if there is one)
						manager.getPlayer().stop(true);

						if (manager.getCurrentSet() != null) {
							manager.getCurrentSet().nextComposition();
						}
					}
				} catch (Exception e) {
					logger.error("Could not automatically stop composition '" + name + "'", e);
				}
			}
		}, maxDurationAndOffset);
	}

	public synchronized void play() throws Exception {
		// Load all files
		loadFiles();

		if (playState != PlayState.PAUSED) {
			// Maybe the composition is stopping meanwhile
			return;
		}

		// All files are loaded -> play the composition (start each file)
		logger.info("Playing composition '" + name + "'");

		for (File file : fileList) {
			if (file.isActive()) {
				file.play();
			}
		}

		startAutoStopTimer(0);

		lastStartTime = LocalDateTime.now();
		passedMillis = 0;

		playState = PlayState.PLAYING;

		if (!defaultComposition) {
			manager.getStateManager().notifyClients();
		}
	}

	public synchronized void pause() throws Exception {
		// Cancel the auto-stop timer
		if (autoStopTimer != null) {
			autoStopTimer.cancel();
			autoStopTimer = null;
		}

		// Save the passed time since the last run
		passedMillis += lastStartTime.until(LocalDateTime.now(), ChronoUnit.MILLIS);

		if (playState == PlayState.PAUSED) {
			return;
		}

		logger.info("Pausing composition '" + name + "'");

		// Pause the composition
		for (File file : fileList) {
			if (file.isActive()) {
				file.pause();
			}
		}

		playState = PlayState.PAUSED;

		if (!defaultComposition) {
			manager.getStateManager().notifyClients();
		}
	}

	public synchronized void resume() throws Exception {
		// Restart the auto-stop timer from the last pause
		startAutoStopTimer(passedMillis);
		lastStartTime = LocalDateTime.now();

		if (playState == PlayState.PLAYING) {
			return;
		}

		logger.info("Resuming composition '" + name + "'");

		// Resume the composition
		for (File file : fileList) {
			if (file.isActive()) {
				file.resume();
			}
		}

		playState = PlayState.PLAYING;

		if (!defaultComposition) {
			manager.getStateManager().notifyClients();
		}
	}

	public synchronized void togglePlay() throws Exception {
		if (playState == PlayState.PLAYING) {
			stop();
		} else {
			play();
		}
	}

	public synchronized void stop(boolean playDefaultComposition) throws Exception {
		playState = PlayState.STOPPING;

		if (!defaultComposition) {
			manager.getStateManager().notifyClients();
		}

		// Cancel the auto-stop timer
		if (autoStopTimer != null) {
			autoStopTimer.cancel();
			autoStopTimer = null;
		}

		passedMillis = 0;

		logger.info("Stopping composition '" + name + "'");

		// Stop the composition
		for (File file : fileList) {
			if (file.isActive()) {
				try {
					file.close();
				} catch (Exception e) {
					logger.error("Could not stop file '" + file.getName() + "'");
				}
			}
		}

		filesLoaded = false;

		logger.info("Composition '" + name + "' stopped");

		playState = PlayState.STOPPED;

		if (!defaultComposition) {
			manager.getStateManager().notifyClients();
		}

		// Play the default composition, if necessary
		if (!defaultComposition && playDefaultComposition) {
			manager.playDefaultComposition();
		}
	}

	public synchronized void stop() throws Exception {
		stop(true);
	}

	@XmlTransient
	public MidiMapping getMidiMapping() {
		return midiMapping;
	}

	public void setMidiMapping(MidiMapping midiMapping) {
		this.midiMapping = midiMapping;
	}

	@XmlElementWrapper(name = "fileList")
	@XmlElements({ @XmlElement(type = MidiFile.class, name = "midiFile"),
			@XmlElement(type = VideoFile.class, name = "videoFile"),
			@XmlElement(type = AudioFile.class, name = "audioFile") })
	public List<File> getFileList() {
		return fileList;
	}

	public void setFileList(List<File> fileList) {
		this.fileList = fileList;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlTransient
	public Manager getManager() {
		return manager;
	}

	public void setManager(Manager manager) {
		this.manager = manager;
	}

	@XmlTransient
	public PlayState getPlayState() {
		return playState;
	}

	public long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(long durationMillis) {
		this.durationMillis = durationMillis;
	}

	@XmlTransient
	public boolean isAutoStartNextComposition() {
		return autoStartNextComposition;
	}

	public void setAutoStartNextComposition(boolean autoStartNextComposition) {
		this.autoStartNextComposition = autoStartNextComposition;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public void setPlayState(PlayState playState) {
		this.playState = playState;
	}

	@XmlTransient
	public boolean isDefaultComposition() {
		return defaultComposition;
	}

	public void setDefaultComposition(boolean defaultComposition) {
		this.defaultComposition = defaultComposition;
	}

	@XmlTransient
	public long getPassedMillis() {
		if(lastStartTime == null) {
			return 0;
		}
		
		return lastStartTime.until(LocalDateTime.now(), ChronoUnit.MILLIS);
	}

	public void setPassedMillis(long passedMillis) {
		this.passedMillis = passedMillis;
	}

}
