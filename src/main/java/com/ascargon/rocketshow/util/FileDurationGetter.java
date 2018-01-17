package com.ascargon.rocketshow.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.midi.MidiPlayer;
import com.ascargon.rocketshow.song.file.AudioFile;
import com.ascargon.rocketshow.song.file.File;
import com.ascargon.rocketshow.song.file.MidiFile;
import com.ascargon.rocketshow.song.file.VideoFile;;

/**
 * Get the duration of a file.
 *
 * @author Moritz A. Vieli
 */
public class FileDurationGetter implements Runnable {

	final static Logger logger = Logger.getLogger(FileDurationGetter.class);

	private static final String DURATION = "ID_LENGTH=";

	private File file;

	public FileDurationGetter(File file) {
		this.file = file;
	}

	private static boolean isDurationLine(String line) {
		return line.startsWith(DURATION);
	}

	private long getDurationWithMplayer(String path) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(new String[] { "mplayer", "-identify", "-frames", "0", path });
		Process process = pb.start();
		BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line = null;

		while ((line = br.readLine()) != null) {
			if (isDurationLine(line)) {
				Double duration = Double.parseDouble(line.trim().substring(DURATION.length()));
				return Math.round(duration) * 1000;
			}
		}

		return 0;
	}

	@Override
	public void run() {
		try {
			if (file instanceof MidiFile) {
				MidiFile midiFile = (MidiFile) file;
				file.setDurationMillis(MidiPlayer.getDuration(midiFile.getPath()));
			} else if (file instanceof AudioFile) {
				AudioFile audioFile = (AudioFile) file;
				file.setDurationMillis(getDurationWithMplayer(audioFile.getPath()));
			} else if (file instanceof VideoFile) {
				VideoFile videoFile = (VideoFile) file;
				file.setDurationMillis(getDurationWithMplayer(videoFile.getPath()));
			}
		} catch (Exception e) {
			logger.error("Could not get duration for file '" + file.getName() + "'", e);
		}
	}

}
