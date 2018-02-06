package com.ascargon.rocketshow.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Settings.AudioOutput;
import com.ascargon.rocketshow.song.file.PlayerLoadedListener;
import com.ascargon.rocketshow.util.ShellManager;

public class AudioPlayer {

	final static Logger logger = Logger.getLogger(AudioPlayer.class);

	public enum PlayerType {
		ALSA_PLAYER, MPLAYER
	}

	private ShellManager shellManager;

	private String path;
	private String device;
	private boolean loop;

	private PlayerType playerType = PlayerType.MPLAYER;

	public void load(PlayerType playerType, PlayerLoadedListener playerLoadedListener, String path,
			AudioOutput audioOutput, String device) throws IOException, InterruptedException {

		this.playerType = playerType;
		this.path = path;
		this.device = device;

		if (playerType == PlayerType.MPLAYER) {
			List<String> params = new ArrayList<String>();

			params.add("mplayer");

			if (audioOutput == AudioOutput.DEVICE) {
				// Set the ALSA out device
				params.add("-ao");
				params.add("alsa:device=" + device);
			}

			// Don't fill the buffer with unnecessary stuff
			params.add("-quiet");

			// Start in slave-mode to process the input easier
			params.add("-slave");

			// Set the cache
			params.add("-cache-min");
			params.add("99");

			// Set the player looped, if necessary
			if (loop) {
				params.add("-loop");

				// 0 = infinite
				params.add("0");
			}

			// Add the path
			params.add(path);

			shellManager = new ShellManager(params.toArray(new String[0]));

			new Thread(new Runnable() {
				public void run() {
					BufferedReader reader = new BufferedReader(new InputStreamReader(shellManager.getInputStream()));
					String line = null;
					try {
						while ((line = reader.readLine()) != null) {
							logger.debug("Output from audio player: " + line);

							if (line.startsWith("Starting playback...")) {
								// Rewind to the start position
								shellManager.sendCommand("pausing seek 0 2", true);

								logger.debug("File '" + path + "' loaded");
								playerLoadedListener.playerLoaded();
							}
						}
					} catch (IOException e) {
						logger.error("Could not read audio player output", e);
					}
				}
			}).start();

			// Pause, as soon as the song has been loaded and wait for it to be
			// played
			pause();
		} else if (playerType == PlayerType.ALSA_PLAYER) {
			logger.debug("File '" + path + "' loaded");
			playerLoadedListener.playerLoaded();
		}
	}

	public void play() throws IOException {
		if (playerType == PlayerType.MPLAYER) {
			shellManager.sendCommand("pause", true);
		} else if (playerType == PlayerType.ALSA_PLAYER) {
			// Buffer-time in microseconds = 100 seconds
			shellManager = new ShellManager(new String[] { "aplay", "-D", "plug:" + device, path, "-B", "100000000" });
		}
	}

	public void pause() throws IOException {
		if (playerType == PlayerType.MPLAYER) {
			shellManager.sendCommand("pause", true);
		}
	}

	public void resume() throws IOException {
		if (playerType == PlayerType.MPLAYER) {
			shellManager.sendCommand("pause", true);
		}
	}

	public void stop() throws Exception {
		if (shellManager != null) {
			if (playerType == PlayerType.MPLAYER) {
				shellManager.sendCommand("quit", true);
				shellManager.getProcess().waitFor();
				shellManager.close();
			} else if (playerType == PlayerType.ALSA_PLAYER) {
				shellManager.getProcess().destroy();
				shellManager.getProcess().waitFor();
			}
		}
	}

	public void close() throws Exception {
		stop();
	}

	public boolean isLoop() {
		return loop;
	}

	public void setLoop(boolean loop) {
		this.loop = loop;
	}

}
