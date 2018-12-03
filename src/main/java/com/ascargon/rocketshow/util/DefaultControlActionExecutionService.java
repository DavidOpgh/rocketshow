package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.*;
import com.ascargon.rocketshow.composition.SetService;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class DefaultControlActionExecutionService implements ControlActionExecutionService {

	private final static Logger logger = LoggerFactory.getLogger(DefaultControlActionExecutionService.class);

	private final PlayerService playerService;
    private final SetService setService;
    private final SettingsService settingsService;

	public DefaultControlActionExecutionService(PlayerService playerService, SetService setService, SettingsService settingsService) {
		this.playerService = playerService;
		this.setService = setService;
		this.settingsService = settingsService;
	}

	private void executeActionOnRemoteDevice(ControlAction controlAction, RemoteDevice remoteDevice) {
		switch (controlAction.getAction()) {
		case PLAY:
			remoteDevice.play();
			break;
		case PLAY_AS_SAMPLE:
			remoteDevice.playAsSample(controlAction.getCompositionName());
			break;
		case PAUSE:
			remoteDevice.pause();
			break;
		case TOGGLE_PLAY:
			remoteDevice.togglePlay();
			break;
		case STOP:
			remoteDevice.stop(true);
			break;
		case NEXT_COMPOSITION:
			remoteDevice.setNextComposition();
			break;
		case PREVIOUS_COMPOSITION:
			remoteDevice.setPreviousComposition();
			break;
		case SELECT_COMPOSITION_BY_NAME:
			remoteDevice.setCompositionName(controlAction.getCompositionName());
			break;
		case SELECT_COMPOSITION_BY_NAME_AND_PLAY:
			remoteDevice.setCompositionName(controlAction.getCompositionName());
			remoteDevice.play();
			break;
		case SET_COMPOSITION_INDEX:
			remoteDevice.setCompositionIndex(setService.getCurrentCompositionIndex());
			break;
		case REBOOT:
			remoteDevice.reboot();
			break;
		default:
			logger.warn("Action '" + controlAction.getAction().toString()
					+ "' is unknown for remote devices and cannot be executed");
			break;
		}
	}

	private void executeActionLocally(ControlAction controlAction) throws Exception {
		// Execute the action locally
		logger.info("Execute action from control event");

		switch (controlAction.getAction()) {
		case PLAY:
			playerService.play();
			break;
		case PLAY_AS_SAMPLE:
            playerService.playAsSample(controlAction.getCompositionName());
			break;
		case PAUSE:
            playerService.pause();
			break;
		case TOGGLE_PLAY:
            playerService.togglePlay();
			break;
		case STOP:
            playerService.stop();
			break;
		case NEXT_COMPOSITION:
            playerService.setNextComposition();
			break;
		case PREVIOUS_COMPOSITION:
            playerService.setPreviousComposition();
			break;
		case SELECT_COMPOSITION_BY_NAME:
			playerService.setCompositionName(controlAction.getCompositionName());
			break;
		case SELECT_COMPOSITION_BY_NAME_AND_PLAY:
			playerService.setCompositionName(controlAction.getCompositionName());
			playerService.play();
			break;
		case REBOOT:
		    // TODO
			//manager.reboot();
			break;
		default:
			logger.warn(
					"Action '" + controlAction.getAction().toString() + "' is locally unknown and cannot be executed");
			break;
		}
	}

	/**
	 * Execute the control action.
	 */
	@Override
	public void execute(ControlAction controlAction) throws Exception {
		if (controlAction.isExecuteLocally()) {
			executeActionLocally(controlAction);
		}

		// Execute the action on each specified remote device
		for (String name : controlAction.getRemoteDeviceNames()) {
			RemoteDevice remoteDevice = settingsService.getRemoteDeviceByName(name);

			if (remoteDevice == null) {
				logger.warn("No remote device could be found in the settings with name " + name);
			} else {
				executeActionOnRemoteDevice(controlAction, remoteDevice);
			}
		}
	}

}
