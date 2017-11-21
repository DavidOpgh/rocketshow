package com.ascargon.rocketshow.rest;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.ascargon.rocketshow.Manager;

@Path("/session")
public class Session {

	@Context
	ServletContext context;
   
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public com.ascargon.rocketshow.Session getSession() throws Exception {
		Manager manager = (Manager)context.getAttribute("manager");
		return manager.getSession();
	}
	
	@Path("wizard-finished")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response update() throws Exception {
		Manager manager = (Manager)context.getAttribute("manager");
		manager.getSession().setFirstStart(false);
		manager.saveSession();
		return Response.status(200).build();
	}
	
}
