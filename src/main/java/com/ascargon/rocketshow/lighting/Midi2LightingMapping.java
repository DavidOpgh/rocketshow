package com.ascargon.rocketshow.lighting;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Midi2LightingMapping {

	public enum MappingType {
		SIMPLE, // The MIDI values 0-126 are mapped to a LIGHTING channel and the
				// value
				// is composed by the velocity multiplied by 2
		EXACT // MIDI channels 0-16 are mapped to a LIGHTING channel and the value is
				// composed by adding the value and the velocity
	}

	private MappingType mappingType = MappingType.SIMPLE;

    @SuppressWarnings("WeakerAccess")
	public MappingType getMappingType() {
		return mappingType;
	}

	@SuppressWarnings("unused")
	public void setMappingType(MappingType mappingType) {
		this.mappingType = mappingType;
	}

}
