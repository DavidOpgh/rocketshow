package com.ascargon.rocketshow.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.ascargon.rocketshow.Manager;

/**
 * Resets all USB interfaces. This is sometimes needed for the raspberry pi to
 * properly connect to USB devices after booting.
 *
 * @author Moritz A. Vieli
 */
public class ResetUsb {

	public static void resetAllInterfaces() throws Exception {
		ProcessBuilder pb = new ProcessBuilder(new String[] { "lsusb" });
		Process process = pb.start();
		BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line = null;

		// Loop over each interface
		while ((line = br.readLine()) != null) {
			// Extract the bus and the device from each line
			String bus = line.substring(4, 7);
			String device = line.substring(15, 18);

			// Reset the interface
			ShellManager shellManager = new ShellManager(
					new String[] { "sudo", Manager.BASE_PATH + "bin/usbreset", "/dev/bus/usb/" + bus + "/" + device });

			shellManager.getProcess().waitFor();
		}
	}

}
