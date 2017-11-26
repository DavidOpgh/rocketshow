package com.ascargon.rocketshow.video;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.song.file.PlayerLoadedListener;
import com.ascargon.rocketshow.util.ShellManager;

public class VideoPlayer {

	final static Logger logger = Logger.getLogger(VideoPlayer.class);

	private ShellManager shellManager;
	
	private Timer loadTimer;
	private Timer closeTimer;
	private boolean closing;

	public void load(PlayerLoadedListener playerLoadedListener, String path) throws IOException, InterruptedException {
		shellManager = new ShellManager(new String[] { "omxplayer", path, "-r", "-b" });

		// Pause, as soon as the song has been loaded and wait for it to be
		// played
		pause();

		// Wait for the player to get ready, because reading the input stream in
		// an infinite loop does not work properly (takes too much resources and
		// exiting the loop as soon as the player is loaded breaks the process)
		loadTimer = new Timer();
		loadTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				loadTimer = null;
				playerLoadedListener.playerLoaded();
			}
		}, 1000 /* TODO Specify in global config */);
	}

	public void play() throws IOException {
		shellManager.sendCommand("p");
	}

	public void pause() throws IOException {
		shellManager.sendCommand("p");
	}

	public void resume() throws IOException {
		shellManager.sendCommand("p");
	}

	public void stop() throws Exception {
		closing = true;
		
		if(loadTimer != null) {
			loadTimer.cancel();
			loadTimer = null;
		}
		
		// Delay a close as backup, because fast load/short may sometimes fail
		closeTimer = new Timer();
		closeTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				closeTimer = null;
				
				if(closing) {
					try {
						shellManager.sendCommand("q");
					} catch (IOException e) {}
				}
			}
		}, 500);
		
		if (shellManager != null) {
			shellManager.sendCommand("q");
			shellManager.getProcess().waitFor();
			shellManager.close();
		}
		
		if(closeTimer != null) {
			closeTimer.cancel();
			closeTimer = null;
		}
		
		closing = false;
	}

	public void close() throws Exception {
		stop();
	}

}
