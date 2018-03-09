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

@Path("/composition")
public class Composition {

	final static Logger logger = Logger.getLogger(Composition.class);

	@Context
	ServletContext context;

	@Path("list")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<com.ascargon.rocketshow.composition.Composition> getAll() throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		return manager.getCompositionManager().getAllCompositions();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public com.ascargon.rocketshow.composition.Composition get(@QueryParam("name") String name) throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		return manager.getCompositionManager().loadComposition(name);
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response save(com.ascargon.rocketshow.composition.Composition composition) throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		manager.getCompositionManager().saveComposition(composition);

		// If this is the current composition, read it again
		if (manager.getCurrentSet() != null && manager.getCurrentSet().getCurrentCompositionName() != null
				&& manager.getCurrentSet().getCurrentCompositionName().equals(composition.getName())) {

			manager.getCurrentSet().readCurrentComposition();
		}

		if(manager.getCurrentSet() != null) {
			manager.loadSet(manager.getCurrentSet().getName());
		}
		
		return Response.status(200).build();
	}

	@Path("delete")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Response delete(@QueryParam("name") String name) throws Exception {
		Manager manager = (Manager) context.getAttribute("manager");
		manager.getCompositionManager().deleteComposition(name);
		
		if(manager.getCurrentSet() != null) {
			manager.loadSet(manager.getCurrentSet().getName());
		}
		
		return Response.status(200).build();
	}

}
