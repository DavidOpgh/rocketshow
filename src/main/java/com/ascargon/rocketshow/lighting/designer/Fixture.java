package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * A Rocket Show Designer fixture.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class Fixture {

    private String uuid;
    private String profileUuid;
    private String name;
    private String dmxUniverseUuid = "";
    private int dmxFirstChannel;
    private String modeShortName;

}
