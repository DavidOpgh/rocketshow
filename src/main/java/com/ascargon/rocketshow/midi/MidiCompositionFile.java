package com.ascargon.rocketshow.midi;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.ascargon.rocketshow.composition.CompositionFile;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("midi")
public class MidiCompositionFile extends CompositionFile {

    private List<MidiRouting> midiRoutingList = new ArrayList<>();

    @XmlElement(name = "midiRouting")
    @XmlElementWrapper(name = "midiRoutingList")
    public List<MidiRouting> getMidiRoutingList() {
        return midiRoutingList;
    }

    public void setMidiRoutingList(List<MidiRouting> midiRoutingList) {
        this.midiRoutingList = midiRoutingList;
    }

    public CompositionFileType getType() {
        return CompositionFileType.MIDI;
    }

}
