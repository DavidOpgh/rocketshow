package com.ascargon.rocketshow.api;

import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;

@Path("/song")
public class Song {

	final static Logger logger = Logger.getLogger(Song.class);

	@Context
	ServletContext context;

	@Path("list")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<com.ascargon.rocketshow.song.Song> getAll() throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		return manager.getSongManager().getAllSongs();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public com.ascargon.rocketshow.song.Song get(@QueryParam("name") String name) throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		return manager.getSongManager().loadSong(name);
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response save(com.ascargon.rocketshow.song.Song song) throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		manager.getSongManager().saveSong(song);

		// If this is the current song, read it again
		if (manager.getCurrentSetList() != null && manager.getCurrentSetList().getCurrentSongName() != null
				&& manager.getCurrentSetList().getCurrentSongName().equals(song.getName())) {

			manager.getCurrentSetList().readCurrentSong();
		}

		if(manager.getCurrentSetList() != null) {
			manager.loadSetList(manager.getCurrentSetList().getName());
		}
		
		return Response.status(200).build();
	}

	@Path("delete")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Response delete(@QueryParam("name") String name) throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		manager.getSongManager().deleteSong(name);
		
		if(manager.getCurrentSetList() != null) {
			manager.loadSetList(manager.getCurrentSetList().getName());
		}
		
		return Response.status(200).build();
	}

}
