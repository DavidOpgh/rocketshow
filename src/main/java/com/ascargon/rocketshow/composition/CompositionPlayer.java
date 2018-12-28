package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.CapabilitiesService;
import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.ActivityNotificationAudioService;
import com.ascargon.rocketshow.api.ActivityNotificationMidiService;
import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.gstreamer.GstApi;
import com.ascargon.rocketshow.midi.*;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import com.sun.jna.Pointer;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.freedesktop.gstreamer.lowlevel.GType;
import org.freedesktop.gstreamer.lowlevel.GValueAPI;
import org.freedesktop.gstreamer.lowlevel.GstStructureAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handle the playing of a single composition.
 */
@Service
public class CompositionPlayer {

    private final static Logger logger = LoggerFactory.getLogger(CompositionPlayer.class);

    private final String uuid = String.valueOf(UUID.randomUUID());

    public enum PlayState {
        PLAYING, // Is the composition playing?
        PAUSED, // Is the composition paused?
        STOPPING, // Is the composition being stopped?
        STOPPED, // Is the composition stopped?
        LOADING, // Is the composition waiting for all files to be loaded to start playing?
        LOADED // Has the composition finished loading all files?
    }

    private final NotificationService notificationService;
    private final ActivityNotificationMidiService activityNotificationMidiService;
    private final PlayerService playerService;
    private final SettingsService settingsService;
    private final MidiRoutingService midiRoutingService;
    private final CapabilitiesService capabilitiesService;
    private final OperatingSystemInformationService operatingSystemInformationService;
    private final ActivityNotificationAudioService activityNotificationAudioService;
    private final SetService setService;

    private Composition composition;
    private PlayState playState = PlayState.STOPPED;
    private long startPosition = 0;

    private final MidiMapping midiMapping = new MidiMapping();

    // Is this the default composition?
    private boolean isDefaultComposition = false;

    // Is this composition played as a sample?
    private boolean isSample = false;

    // The gstreamer pipeline, used to sync all files in this composition
    private Pipeline pipeline;

    public CompositionPlayer(NotificationService notificationService, ActivityNotificationMidiService activityNotificationMidiService, PlayerService playerService, SettingsService settingsService, MidiRoutingService midiRoutingService, CapabilitiesService capabilitiesService, OperatingSystemInformationService operatingSystemInformationService, ActivityNotificationAudioService activityNotificationAudioService, SetService setService) {
        this.notificationService = notificationService;
        this.activityNotificationMidiService = activityNotificationMidiService;
        this.playerService = playerService;
        this.settingsService = settingsService;
        this.midiRoutingService = midiRoutingService;
        this.capabilitiesService = capabilitiesService;
        this.operatingSystemInformationService = operatingSystemInformationService;
        this.activityNotificationAudioService = activityNotificationAudioService;
        this.setService = setService;

        this.midiMapping.setParent(settingsService.getSettings().getMidiMapping());
    }

    // Taken from gstreamers gstfluiddec.c -> handle_buffer
    private void processMidiBuffer(ByteBuffer byteBuffer, List<MidiRouting> midiRoutingList) {
        int event = byteBuffer.get(0);
        int type = event & 0xf0;

        if (type != 0xf0) {
            // Common messages
            int channel = event & 0x0f;
            int command = event & 0xf0;
            int note = byteBuffer.get(1) & 0x7f;
            int velocity = byteBuffer.get(2) & 0x7f;

            MidiSignal midiSignal = new MidiSignal();
            midiSignal.setChannel(channel);
            midiSignal.setCommand(command);
            midiSignal.setNote(note);
            midiSignal.setVelocity(velocity);

            midiRoutingService.sendSignal(midiSignal, midiRoutingList);

            activityNotificationMidiService.notifyClients(midiSignal, MidiSignal.MidiDirection.IN, MidiSignal.MidiSource.MIDI_FILE, null);
        }
    }

    private int getAudioBusStartChannel(AudioBus audioBus) {
        int startChannelIndex = 0;

        // Get the starting channel of the current bus
        for (AudioBus settingsAudioBus : settingsService.getSettings().getAudioBusList()) {
            if (settingsAudioBus.getName().equals(audioBus.getName())) {
                break;
            } else {
                startChannelIndex += settingsAudioBus.getChannels();
            }
        }

        return startChannelIndex;
    }

    private float getChannelVolume(AudioBus audioBus, int outputChannelIndex, int inputChannelIndex) {
        int startChannelIndex = getAudioBusStartChannel(audioBus);

        if (outputChannelIndex < startChannelIndex) {
            return 0;
        }

        if (inputChannelIndex >= audioBus.getChannels()) {
            return 0;
        }

        if (inputChannelIndex == outputChannelIndex - startChannelIndex) {
            return 1;
        } else {
            return 0;
        }
    }

    // Load all files and construct the complete GST pipeline
    public void loadFiles() throws Exception {
        boolean hasAudioFile = false;

        if (playState != PlayState.STOPPED) {
            return;
        }

        if (!capabilitiesService.getCapabilities().isGstreamer()) {
            throw new Exception("Gstreamer is not available");
        }

        // At least one active file
        boolean hasActiveFile = false;

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive()) {
                hasActiveFile = true;
                break;
            }
        }

        if (!hasActiveFile) {
            // No need to play anything
            return;
        }

        playState = PlayState.LOADING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }

        logger.debug(
                "Loading composition '" + composition.getName() + "...");

        if (pipeline != null) {
            pipeline.stop();
        }

        pipeline = new Pipeline();

        Bus bus = pipeline.getBus();

        Element audioMixer = null;

        // Create an audiomixer, if at least one audiosource is present
        for (int i = 0; i < composition.getCompositionFileList().size(); i++) {
            CompositionFile compositionFile = composition.getCompositionFileList().get(i);

            if (compositionFile.isActive() && compositionFile instanceof AudioCompositionFile) {
                hasAudioFile = true;
                break;
            }
        }


        bus.connect((Bus.ERROR) (GstObject source, int code, String message) -> {
            logger.error("GST: " + message);
            try {
                notificationService.notifyClients(message);
            } catch (Exception e) {
                logger.error("Could not notify clients about an error", e);
            }
            try {
                stop();
            } catch (Exception e) {
                logger.error("Could not stop compostion triggered by an error", e);
            }
        });
        bus.connect((Bus.WARNING) (GstObject source, int code, String message) -> logger.warn("GST: " + message));
        bus.connect((Bus.INFO) (GstObject source, int code, String message) -> logger.warn("GST: " + message));
        bus.connect((GstObject source, State old, State newState, State pending) -> {
            if (source.getTypeName().equals("GstPipeline")) {
                if (newState == State.PLAYING) {
                    // We changed to playing, maybe we need to seek to the start position (not possible before playing)
                    if (startPosition > 0) {
                        try {
                            seek(startPosition);
                        } catch (Exception e) {
                            logger.error("Could not set start position when changed to playing", e);
                        }
                        startPosition = 0;
                    }

                    playState = PlayState.PLAYING;

                    if (!isDefaultComposition && !isSample) {
                        try {
                            notificationService.notifyClients(playerService, setService);
                        } catch (Exception e) {
                            logger.error("Could not notify clients about a playing event", e);
                        }
                    }
                }
            }
        });
        bus.connect((Bus.EOS) source -> {
            try {
                playerService.compositionPlayerFinishedPlaying(this);
            } catch (Exception e) {
                logger.error("Could not stop the composition after end of stream", e);
            }
        });
        bus.connect((Bus bus1, Message message) -> {
            if (message.getType().equals(MessageType.ELEMENT)) {
                Structure structure = message.getStructure();

                if (structure.getName().equals("level")) {
                    try {
                        // We got a level message
                        activityNotificationAudioService.notifyClients(structure.getDoubles("peak"));
                    } catch (Exception e) {
                        logger.error("Could not process level message", e);
                    }
                }
            }
        });

        if (hasAudioFile) {
            audioMixer = ElementFactory.make("audiomixer", "audiomixer");
            pipeline.add(audioMixer);

            // Add a capsfilter to enforce multi-channel out. Otherwise only 2 will be mixed
            Element capsFilter  = ElementFactory.make("capsfilter", "capsfilter");
            Caps caps = GstApi.GST_API.gst_caps_from_string("audio/x-raw,channels=" + settingsService.getTotalAudioChannels());
            capsFilter.set("caps", caps);
            pipeline.add(capsFilter);

            audioMixer.link(capsFilter);

            String sinkName = "alsasink";

            if (OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
                sinkName = "osxaudiosink";
            }
            BaseSink sink = (BaseSink) ElementFactory.make(sinkName, "audiosink");

            if (!OperatingSystemInformation.Type.OS_X.equals(operatingSystemInformationService.getOperatingSystemInformation().getType())) {
                sink.set("device", "rocketshow");
            }
            pipeline.add(sink);

            Element level = null;
            if (!isSample) {
                level = ElementFactory.make("level", "level");
                // 500 Milliseconds
                level.set("interval", 500 * 1000000);
                level.set("post-messages", true);
                pipeline.add(level);
            }

            if (level == null) {
                capsFilter.link(sink);
            } else {
                capsFilter.link(level);
                level.link(sink);
            }
        }

        // Load all files, create the pipeline and handle exceptions to pipeline-playing
        for (int i = 0; i < composition.getCompositionFileList().size(); i++) {
            CompositionFile compositionFile = composition.getCompositionFileList().get(i);

            if (compositionFile.isActive()) {
                if (compositionFile instanceof MidiCompositionFile) {
                    MidiCompositionFile midiCompositionFile = (MidiCompositionFile) compositionFile;

                    for (MidiRouting midiRouting : midiCompositionFile.getMidiRoutingList()) {
                        midiRouting.getMidiMapping().setParent(midiMapping);
                    }

                    Element midiFileSource = ElementFactory.make("filesrc", "midifilesrc" + i);
                    midiFileSource.set("location", settingsService.getSettings().getBasePath() + "/" + settingsService.getSettings().getMediaPath() + "/" + settingsService.getSettings().getMidiPath() + "/" + compositionFile.getName());
                    pipeline.add(midiFileSource);

                    Element midiParse = ElementFactory.make("midiparse", "midiparse" + i);
                    pipeline.add(midiParse);

                    AppSink midiSink = (AppSink) ElementFactory.make("appsink", "midisink" + i);
                    // Required to actually send the signals
                    midiSink.set("emit-signals", true);
                    pipeline.add(midiSink);

                    // Sometimes preroll and sometimes new-sample events get fired. We have
                    // to process both.
                    midiSink.connect((AppSink.NEW_SAMPLE) element -> {
                        Sample sample = element.pullSample();
                        processMidiBuffer(sample.getBuffer().map(false), midiCompositionFile.getMidiRoutingList());
                        sample.dispose();
                        return FlowReturn.OK;
                    });
                    midiSink.connect((AppSink.NEW_PREROLL) elem -> {
                        Sample sample = elem.pullPreroll();
                        processMidiBuffer(sample.getBuffer().map(false), midiCompositionFile.getMidiRoutingList());
                        sample.dispose();
                        return FlowReturn.OK;
                    });

                    midiFileSource.link(midiParse);
                    midiParse.link(midiSink);
                } else if (compositionFile instanceof AudioCompositionFile && capabilitiesService.getCapabilities().isGstreamer()) {
                    logger.debug("Add audio file to pipeline");

                    AudioCompositionFile audioCompositionFile = (AudioCompositionFile) compositionFile;

                    URIDecodeBin audioSource = (URIDecodeBin) ElementFactory.make("uridecodebin", "audiouridecodebin" + i);
                    audioSource.set("uri", "file://" + settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getAudioPath() + File.separator + compositionFile.getName());
                    pipeline.add(audioSource);

                    // Add a queue after reading the source to make use of multithreading and
                    // queue reading from possibly slow sources.
                    Element audioqueue = ElementFactory.make("queue", "audioqueue" + i);
                    audioSource.connect((Element.PAD_ADDED) (Element element, Pad pad) -> {
                        String name = pad.getCaps().getStructure(0).getName();

                        if ("audio/x-raw-float".equals(name) || "audio/x-raw-int".equals(name) || "audio/x-raw".equals(name)) {
                            pad.link(audioqueue.getSinkPads().get(0));
                        }
                    });
                    pipeline.add(audioqueue);

                    Element audioconvert = ElementFactory.make("audioconvert", "audioconvert" + i);
                    pipeline.add(audioconvert);
                    audioqueue.link(audioconvert);

                    // Apply the mix matrix
                    GValueAPI.GValue mixMatrix = new GValueAPI.GValue();
                    GValueAPI.GVALUE_API.g_value_init(mixMatrix, GstApi.GST_API.gst_value_array_get_type());

                    AudioBus audioBus = settingsService.getAudioBusFromName(audioCompositionFile.getOutputBus());

                    // Repeat for each output channel
                    for (int j = 0; j < settingsService.getTotalAudioChannels(); j++) {
                        GValueAPI.GValue outputChannel = new GValueAPI.GValue();
                        GValueAPI.GVALUE_API.g_value_init(outputChannel, GstApi.GST_API.gst_value_array_get_type());

                        // Fill the channel with the input channels
                        for (int k = 0; k < audioCompositionFile.getChannels(); k++) {
                            GValueAPI.GValue inputChannel = new GValueAPI.GValue(GType.FLOAT);

                            float channelVolume = getChannelVolume(audioBus, j, k);

                            inputChannel.setValue(channelVolume);
                            GstApi.GST_API.gst_value_array_append_value(outputChannel, inputChannel.getPointer());
                            GValueAPI.GVALUE_API.g_value_unset(inputChannel);
                        }

                        GstApi.GST_API.gst_value_array_append_value(mixMatrix, outputChannel.getPointer());
                        GValueAPI.GVALUE_API.g_value_unset(outputChannel);
                    }

                    logger.debug("Mix-matrix for " + audioCompositionFile.getName() + ": " + mixMatrix.toString());

                    GstApi.GST_API.g_object_set_property(audioconvert, "mix-matrix", mixMatrix.getPointer());
                    GValueAPI.GVALUE_API.g_value_unset(mixMatrix);

                    // Link the converter to the mixer
                    audioconvert.link(audioMixer);
                } else if (compositionFile instanceof VideoCompositionFile && capabilitiesService.getCapabilities().isGstreamer()) {
                    logger.debug("Add video file to pipeline");

                    // Does not work on OS X
                    // See http://gstreamer-devel.966125.n4.nabble.com/OpenGL-renderer-window-td4686092.html

                    PlayBin playBin = (PlayBin) ElementFactory.make("playbin", "playbin" + i);
                    playBin.set("uri", "file://" + settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getVideoPath() + File.separator + compositionFile.getName());
                    pipeline.add(playBin);
                }
            }
        }

        logger.debug("Composition '" + composition.getName() + "' loaded");

        // Maybe we are stopping meanwhile
        if (playState == PlayState.LOADING && !isDefaultComposition && !isSample) {
            playState = PlayState.LOADED;
            notificationService.notifyClients(playerService, setService);
        }
    }

    public void play() throws Exception {
        if (composition == null) {
            return;
        }

        // Load the files, if not already done by a previously by a separate call
        loadFiles();

        // All files are loaded -> play the composition (start each file)
        logger.info("Playing composition '" + composition.getName() + "'...");

        if (pipeline != null) {
            pipeline.play();
        }
    }

    public void pause() throws Exception {
        if (playState == PlayState.PAUSED) {
            return;
        }

        logger.info("Pausing composition '" + composition.getName() + "'");

        // Pause the composition
        if (pipeline != null) {
            pipeline.pause();
        }

        playState = PlayState.PAUSED;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    public void togglePlay() throws Exception {
        if (playState == PlayState.PLAYING) {
            stop();
        } else {
            play();
        }
    }

    public void stop() throws Exception {
        if (composition == null) {
            return;
        }

        playState = PlayState.STOPPING;
        startPosition = 0;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }

        logger.info("Stopping composition '" + composition.getName() + "'");

        // Stop the composition
        if (pipeline != null) {
            pipeline.stop();
            pipeline = null;
        }

        playState = PlayState.STOPPED;

        if (!isSample && !isDefaultComposition) {
            notificationService.notifyClients(playerService, setService);
        }

        logger.info("Composition '" + composition.getName() + "' stopped");
    }

    public void seek(long positionMillis) throws Exception {
        // When we seek before pressing play
        startPosition = positionMillis;

        logger.debug("Seek to position " + positionMillis);

        if (pipeline != null) {
            pipeline.seek(positionMillis, TimeUnit.MILLISECONDS);
        }

        if (!isSample) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    public long getPositionMillis() {
        if (startPosition > 0) {
            return startPosition;
        }

        if (composition == null) {
            return 0;
        }

        if (pipeline != null) {
            return pipeline.queryPosition(TimeUnit.MILLISECONDS);
        }

        return 0;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof CompositionPlayer) {
            CompositionPlayer compositionPlayer = (CompositionPlayer) object;

            return this.uuid.equals(compositionPlayer.uuid);
        }

        return false;
    }

    public PlayState getPlayState() {
        return playState;
    }

    public void setPlayState(PlayState playState) {
        this.playState = playState;
    }

    public Composition getComposition() {
        return composition;
    }

    public void setComposition(Composition composition) throws Exception {
        this.composition = composition;

        if (!isSample && !isDefaultComposition) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    public boolean isDefaultComposition() {
        return isDefaultComposition;
    }

    public void setDefaultComposition(boolean defaultComposition) {
        this.isDefaultComposition = defaultComposition;
    }

    public boolean isSample() {
        return isSample;
    }

    public void setSample(boolean sample) {
        isSample = sample;
    }

}
