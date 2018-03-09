package com.ascargon.rocketshow.composition;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A composition inside a set containing less information than a "real"
 * composition.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class SetComposition {

	private String name;
	private long durationMillis;
	private boolean autoStartNextComposition = false;

	@XmlElement
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlElement
	public long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(long durationMillis) {
		this.durationMillis = durationMillis;
	}

	@XmlElement
	public boolean isAutoStartNextComposition() {
		return autoStartNextComposition;
	}

	public void setAutoStartNextComposition(boolean autoStartNextComposition) {
		this.autoStartNextComposition = autoStartNextComposition;
	}

}
