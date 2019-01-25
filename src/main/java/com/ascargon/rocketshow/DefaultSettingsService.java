package com.ascargon.rocketshow;

import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.midi.MidiDevice;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiService;
import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.raspberry.RaspberryResetUsbService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.util.ShellManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiUnavailableException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultSettingsService implements SettingsService {

    private final static Logger logger = LoggerFactory.getLogger(Settings.class);

    private final String FILE_NAME = "settings";

    private final String ROCKET_SHOW_SETTINGS_START = "# ROCKETSHOWSTART";
    private final String ROCKET_SHOW_SETTINGS_END = "# ROCKETSHOWEND";

    private final OperatingSystemInformationService operatingSystemInformationService;
    private final RaspberryResetUsbService raspberryResetUsbService;
    private final MidiService midiService;

    private Settings settings;

    private final ApplicationHome applicationHome = new ApplicationHome(RocketShowApplication.class);

    public DefaultSettingsService(RaspberryResetUsbService raspberryResetUsbService, OperatingSystemInformationService operatingSystemInformationService, MidiService midiService) {
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.raspberryResetUsbService = raspberryResetUsbService;
        this.midiService = midiService;

        initDefaultSettings();

        // Load the settings
        try {
            load();
        } catch (Exception e) {
            logger.error("Could not load the settings", e);
        }

        // Save the settings (in case none were already existant)
        try {
            save();
        } catch (JAXBException e) {
            logger.error("Could not save settings", e);
        }
    }

    private void initDefaultSettings() {
        // Initialize default settings

        settings = new Settings();

        settings.setBasePath(applicationHome.getDir().toString() + File.separator);
        settings.setMediaPath("media");
        settings.setAudioPath("audio");
        settings.setMidiPath("midi");
        settings.setVideoPath("video");
        settings.setLeadSheetPath("leadsheet");

        settings.setMidiInDevice(new MidiDevice());
        settings.setMidiOutDevice(new MidiDevice());

        // Add the default audio bus
        AudioBus audioBus = new AudioBus();
        audioBus.setName("My audio bus 1");
        audioBus.setChannels(2);
        settings.getAudioBusList().add(audioBus);

        // Global MIDI mapping
        settings.setMidiMapping(new MidiMapping());

        try {
            List<MidiDevice> midiInDeviceList;
            midiInDeviceList = midiService.getMidiDevices(MidiSignal.MidiDirection.IN);
            if (midiInDeviceList.size() > 0) {
                settings.setMidiInDevice(midiInDeviceList.get(0));
            }
        } catch (MidiUnavailableException e) {
            logger.error("Could not get any MIDI IN devices", e);
        }

        try {
            List<MidiDevice> midiOutDeviceList;
            midiOutDeviceList = midiService.getMidiDevices(MidiSignal.MidiDirection.OUT);
            if (midiOutDeviceList.size() > 0) {
                settings.setMidiOutDevice(midiOutDeviceList.get(0));
            }
        } catch (MidiUnavailableException e) {
            logger.error("Could not get any MIDI OUT devices", e);
        }

        settings.setLightingSendDelayMillis(10);

        settings.setOffsetMillisAudio(0);
        settings.setOffsetMillisMidi(0);
        settings.setOffsetMillisVideo(0);

        if (OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            settings.setAudioOutput(Settings.AudioOutput.HEADPHONES);
        } else if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            settings.setAudioOutput(Settings.AudioOutput.DEFAULT);
        } else if (OperatingSystemInformation.Type.LINUX.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            settings.setAudioOutput(Settings.AudioOutput.DEVICE);
        }

        settings.setAudioRate(44100 /* or 48000 */);
        settings.setAlsaPeriodSize(16384);
        settings.setAlsaBufferSize(5);

        settings.setLoggingLevel(Settings.LoggingLevel.INFO);

        if (OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            settings.setEnableRaspberryGpio(true);
            settings.setWlanApEnable(true);
        }

        Instrument instrument;

        instrument = new Instrument();
        instrument.setName("Vocals");
        instrument.setUuid(UUID.randomUUID().toString());
        settings.getInstrumentList().add(instrument);

        instrument = new Instrument();
        instrument.setName("Guitar");
        instrument.setUuid(UUID.randomUUID().toString());
        settings.getInstrumentList().add(instrument);

        instrument = new Instrument();
        instrument.setName("Bass");
        instrument.setUuid(UUID.randomUUID().toString());
        settings.getInstrumentList().add(instrument);

        instrument = new Instrument();
        instrument.setName("Horns");
        instrument.setUuid(UUID.randomUUID().toString());
        settings.getInstrumentList().add(instrument);
    }

    @Override
    public AudioBus getAudioBusFromName(String outputBus) {
        if (outputBus == null) {
            if (settings.getAudioBusList().size() > 0) {
                return settings.getAudioBusList().get(0);
            } else {
                return null;
            }
        }

        // Get an alsa device name from a bus name
        for (AudioBus audioBus : settings.getAudioBusList()) {
            if (outputBus.equals(audioBus.getName())) {
                return audioBus;
            }
        }

        // Return a default bus, if none is found
        if (settings.getAudioBusList().size() > 0) {
            return settings.getAudioBusList().get(0);
        }

        return null;
    }

    @Override
    public RemoteDevice getRemoteDeviceByName(String name) {
        for (RemoteDevice remoteDevice : settings.getRemoteDeviceList()) {
            if (remoteDevice.getName().equals(name)) {
                return remoteDevice;
            }
        }

        return null;
    }

    private void setSystemAudioOutput(int id) throws Exception {
        if (!OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            return;
        }

        ShellManager shellManager = new ShellManager(new String[]{"amixer", "cset", "numid=3", String.valueOf(id)});
        shellManager.getProcess().waitFor();
    }

    @Override
    public int getTotalAudioChannels() {
        int total = 0;

        for (AudioBus audioBus : settings.getAudioBusList()) {
            total += audioBus.getChannels();
        }

        return total;
    }

    private String getBusNameFromId(int id) {
        return "bus" + (id + 1);
    }

    @Override
    public String getAlsaDeviceFromOutputBus(String outputBus) {
        // Get an alsa device name from a bus name
        for (int i = 0; i < settings.getAudioBusList().size(); i++) {
            AudioBus audioBus = settings.getAudioBusList().get(i);

            logger.debug("Got bus '" + audioBus.getName() + "'");

            if (outputBus != null && outputBus.equals(audioBus.getName())) {
                logger.debug("Found device '" + getBusNameFromId(i) + "'");

                return getBusNameFromId(i);
            }
        }

        // Return a default bus, if none is found
        if (settings.getAudioBusList().size() > 0) {
            return getBusNameFromId(0);
        }

        return "";
    }

    private String getAlsaSettings() {
        // Generate the ALSA settings
        StringBuilder alsaSettings = new StringBuilder();
        int currentChannel = 0;

        if (settings.getAudioDevice() == null) {
            // We got no audio device
            return "";
        }

        alsaSettings.append(ROCKET_SHOW_SETTINGS_START);
        alsaSettings.append(System.lineSeparator());

        // Build the dshare device
        alsaSettings.append(
                "pcm.dshare {\n" +
                        "  type dmix\n" +
                        "  ipc_key 2048\n" +
                        "  slave {\n" +
                        "    pcm \"hw:").append(settings.getAudioDevice().getKey()).append("\"\n")
                .append("    channels ").append(getTotalAudioChannels()).append("\n");

        if (settings.getAlsaPeriodTime() != null) {
            alsaSettings.append("    period_time ").append(settings.getAlsaPeriodTime()).append("\n");
        }

        if (settings.getAlsaPeriodSize() != null) {
            alsaSettings.append("    period_size ").append(settings.getAlsaPeriodSize()).append("\n");
        }

        if (settings.getAlsaBufferSize() != null && settings.getAlsaPeriodSize() != null) {
            alsaSettings.append("    buffer_size ").append(settings.getAlsaBufferSize() * settings.getAlsaPeriodSize()).append("\n");
        }

        alsaSettings.append("  }\n")
                .append("  bindings {\n");

        // Add all channels
        for (int i = 0; i < getTotalAudioChannels(); i++) {
            alsaSettings.append("    ").append(i).append(" ").append(i).append("\n");
        }

        alsaSettings.append("  }\n" + "}\n");

        // Create the default bus
        alsaSettings.append("\n" + "pcm.").append("rocketshow").append(" {\n").append("  type plug\n").append("  slave {\n").append("    pcm \"dshare\"\n").append("    channels ").append(getTotalAudioChannels()).append("\n").append("  }\n");

        // Add each channel to the bus
        for (int j = 0; j < getTotalAudioChannels(); j++) {
            alsaSettings.append("  ttable.").append(j).append(".").append(currentChannel).append(" 1\n");
            currentChannel++;
        }

        alsaSettings.append("}\n");

        alsaSettings.append(ROCKET_SHOW_SETTINGS_END);

        return alsaSettings.toString();
    }

    private void updateAudioSystem() throws Exception {
        if (settings.getAudioOutput() == Settings.AudioOutput.HEADPHONES && OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            setSystemAudioOutput(1);
        } else if (settings.getAudioOutput() == Settings.AudioOutput.HDMI && OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            setSystemAudioOutput(2);
        } else if (settings.getAudioOutput() == Settings.AudioOutput.DEVICE) {
            // Write the audio settings to /home/.asoundrc and use ALSA to
            // output audio on the selected device name
            logger.debug("Write ALSA settings");

            String alsaSettingsPath = System.getProperty("user.home") + "/.asoundrc";
            StringBuilder existingAlsaSettings = new StringBuilder();
            File alsaSettings = new File(alsaSettingsPath);
            boolean isInRocketShowSection = false;

            // Read the existing .asoundrc (without existing Rocket Show settings)
            if (alsaSettings.exists()) {
                BufferedReader bufferedReader;
                try {
                    bufferedReader = new BufferedReader(new FileReader(alsaSettingsPath));
                    String line = bufferedReader.readLine();
                    while (line != null) {
                        if (ROCKET_SHOW_SETTINGS_START.equals(line)) {
                            isInRocketShowSection = true;
                        } else if (ROCKET_SHOW_SETTINGS_END.equals(line)) {
                            isInRocketShowSection = false;
                        } else {
                            if (!isInRocketShowSection) {
                                existingAlsaSettings.append(line);
                                existingAlsaSettings.append(System.lineSeparator());
                            }
                        }

                        // Read the next line
                        line = bufferedReader.readLine();
                    }
                    bufferedReader.close();
                } catch (IOException e) {
                    logger.error("Could not read ALSA settings on '" + alsaSettingsPath + "'", e);
                }

                if (existingAlsaSettings.length() > 0 && !existingAlsaSettings.toString().endsWith(System.lineSeparator())) {
                    existingAlsaSettings.append(System.lineSeparator());
                }
            }

            // Create a new file containing the old settings and the new Rocket Show settings
            try {
                FileWriter fileWriter = new FileWriter(new File(alsaSettingsPath), false);
                fileWriter.write(existingAlsaSettings.toString() + getAlsaSettings());
                fileWriter.close();
            } catch (IOException e) {
                logger.error("Could not write .asoundrc", e);
            }
        }
    }

    private void updateLoggingLevel() {
        // Set the proper logging level (map from the log4j enum to our own
        // enum)

        switch (settings.getLoggingLevel()) {
            case INFO:
                Configurator.setRootLevel(Level.INFO);
                break;
            case WARN:
                Configurator.setRootLevel(Level.WARN);
                break;
            case ERROR:
                Configurator.setRootLevel(Level.ERROR);
                break;
            case DEBUG:
                Configurator.setRootLevel(Level.DEBUG);
                break;
            case TRACE:
                Configurator.setRootLevel(Level.TRACE);
                break;
        }
    }

    private void updateWlanAp() {
        String apConfig = "";
        String statusCommand;

        if (!settings.isWlanApEnable()) {
            return;
        }

        // Update the access point configuration
        apConfig += "interface=wlan0\n";
        apConfig += "driver=nl80211\n";
        apConfig += "ssid=" + settings.getWlanApSsid() + "\n";
        apConfig += "utf8_ssid=1\n";
        apConfig += "hw_mode=g\n";
        apConfig += "channel=7\n";
        apConfig += "wmm_enabled=0\n";
        apConfig += "macaddr_acl=0\n";
        apConfig += "auth_algs=1\n";

        if (settings.isWlanApSsidHide()) {
            apConfig += "ignore_broadcast_ssid=1\n";
        } else {
            apConfig += "ignore_broadcast_ssid=0\n";
        }

        if (settings.getWlanApPassphrase() != null && settings.getWlanApPassphrase().length() >= 8) {
            apConfig += "wpa=2\n";
            apConfig += "wpa_passphrase=" + settings.getWlanApPassphrase() + "\n";
        }

        apConfig += "wpa_key_mgmt=WPA-PSK\n";
        apConfig += "wpa_pairwise=TKIP\n";
        apConfig += "rsn_pairwise=CCMP\n";

        try {
            FileWriter fileWriter = new FileWriter("/etc/hostapd/hostapd.conf", false);
            fileWriter.write(apConfig);
            fileWriter.close();
        } catch (IOException e) {
            logger.error("Could not write /etc/hostapd/hostapd.conf", e);
        }

        // Activate/deactivate the access point completely
        if (settings.isWlanApEnable()) {
            statusCommand = "enable";
        } else {
            statusCommand = "disable";
        }

        try {
            new ShellManager(new String[]{"sudo", "systemctl", statusCommand, "hostapd"});
        } catch (IOException e) {
            logger.error("Could not update the access point status with '" + statusCommand + "'", e);
        }
    }

    private void updateSystem() {
        // Update all system settings

        if (OperatingSystemInformation.Type.LINUX.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
            try {
                updateAudioSystem();
            } catch (Exception e) {
                logger.error("Could not update the audio system settings", e);
            }
        }

        try {
            updateLoggingLevel();
        } catch (Exception e) {
            logger.error("Could not update the logging level system settings", e);
        }

        if (OperatingSystemInformation.SubType.RASPBIAN.equals(operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            try {
                updateWlanAp();
            } catch (Exception e) {
                logger.error("Could not update the wireless access point settings", e);
            }
        }
    }

    @Override
    public void save() throws JAXBException {
        File file = new File(applicationHome.getDir() + File.separator + FILE_NAME + ".xml");
        JAXBContext jaxbContext = JAXBContext.newInstance(Settings.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        // output pretty printed
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        jaxbMarshaller.marshal(settings, file);

        logger.info("Settings saved");
    }

    @Override
    public void load() throws Exception {
        File file = new File(applicationHome.getDir() + File.separator + FILE_NAME + ".xml");

        if (!file.exists() || file.isDirectory()) {
            logger.info("Settings file does not exist");
            return;
        }

        // Restore the session from the file
        JAXBContext jaxbContext = JAXBContext.newInstance(Settings.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        this.setSettings((Settings) jaxbUnmarshaller.unmarshal(file));

        // Reset the USB interface, if needed
        try {
            if (settings.isResetUsbAfterBoot()) {
                logger.info("Resetting all USB devices");
                raspberryResetUsbService.resetAllInterfaces(settings.getBasePath());
            }
        } catch (Exception e) {
            logger.error("Could not reset the USB devices", e);
        }

        logger.info("Settings loaded");
    }

    @Override
    public Settings getSettings() {
        return settings;
    }

    @Override
    public void setSettings(Settings settings) {
        this.settings = settings;
        updateSystem();
    }

}
