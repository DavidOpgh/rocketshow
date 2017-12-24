package com.ascargon.rocketshow.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.codehaus.jackson.annotate.JsonIgnore;

@XmlRootElement
public class MidiSignal {

	private int command;
	private int channel;
	private int note;
	private int velocity;

	public MidiSignal() {
	}

	public MidiSignal(ShortMessage shortMessage) {
		command = shortMessage.getCommand();
		channel = shortMessage.getChannel();
		note = shortMessage.getData1();
		velocity = shortMessage.getData2();
	}

	// Also use the @JsonIgnore annotation, because the state including this
	// class is serialized with a JSON serializer in contrast to the rest of the
	// app.
	@XmlTransient
	@JsonIgnore
	public ShortMessage getShortMessage() throws InvalidMidiDataException {
		ShortMessage shortMessage = new ShortMessage();
		shortMessage.setMessage(command, channel, note, velocity);

		return shortMessage;
	}

	@XmlElement
	public int getCommand() {
		return command;
	}

	public void setCommand(int command) {
		this.command = command;
	}

	@XmlElement
	public int getChannel() {
		return channel;
	}

	public void setChannel(int channel) {
		this.channel = channel;
	}

	@XmlElement
	public int getNote() {
		return note;
	}

	public void setNote(int note) {
		this.note = note;
	}

	@XmlElement
	public int getVelocity() {
		return velocity;
	}

	public void setVelocity(int velocity) {
		this.velocity = velocity;
	}

}