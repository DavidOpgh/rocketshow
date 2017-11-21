package com.ascargon.rocketshow;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Session {

	private String currentSetListName;
	private boolean firstStart = true;
	
	public Session() {
	}
	
	@XmlElement
	public String getCurrentSetListName() {
		return currentSetListName;
	}

	public void setCurrentSetListName(String currentSetListName) {
		this.currentSetListName = currentSetListName;
	}

	@XmlElement
	public boolean isFirstStart() {
		return firstStart;
	}

	public void setFirstStart(boolean firstStart) {
		this.firstStart = firstStart;
	}
	
}
