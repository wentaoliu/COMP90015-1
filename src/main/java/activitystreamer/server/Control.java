package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;

public class Control extends Thread {

	// Represents servers in the same network,
	// directly or indirectly connected to this server.
	private class ServerInfo {

		private String id;
		private String hostname;
		private int port;
		private int load;

		public ServerInfo(String id, String hostname, int port, int load) {
			this.id = id;
			this.hostname = hostname;
			this.port = port;
			this.load = load;
		}

		public String getServerId() {
			return id;
		}


		public String getHostname() {
			return hostname;
		}

		public void setHostname(String hostname) {
			this.hostname = hostname;
		}

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public int getLoad() {
			return load;
		}

		public void setLoad(int load) {
			this.load = load;
		}
	}

	// Represents users connected to this server
	private class UserInfo {
		private String username;
		private String secret;

		public UserInfo(String username, String secret) {
			this.username = username;
			this.secret = secret;
		}

		public String getUsername() {
			return username;
		}

		public String getSecret() {
			return secret;
		}
	}

	// logger
	private static final Logger log = LogManager.getLogger();

	// server id
	private static String serverId = Settings.nextSecret();

	// List of all connections
	private static ArrayList<Connection> connections = new ArrayList<>();

	// List of validated server connections
	private static ArrayList<Connection> validatedServerConnections = new ArrayList<>();
	// List of validated client connections
	private static ArrayList<Connection> validatedClientConnections = new ArrayList<>();

	// List of all server info in this network (obtained from SERVER_ANNOUNCE)
	private static ArrayList<ServerInfo> allKnownServers = new ArrayList<>();

	private static boolean term=false;
	private static Listener listener;

	// List of all users registered on this server (online or offline)
	private static ArrayList<UserInfo> registeredUsers = new ArrayList<>();

	// For register workflow:
	// username & source client connection
	private static Map<String, Connection> registerRequestSources =  new HashMap<>();
	// username & source server connection
	private static Map<String, Connection> lockRequestSources = new HashMap<>();
	// username & number of servers waiting for response
	private static Map<String, Integer> pendingRequests  = new HashMap<>();

	
	protected static Control control = null;
	
	public static Control getInstance() {
		if(control==null){
			control=new Control();
		} 
		return control;
	}

	private Control() {
		// start a listener
		try {
			listener = new Listener();
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: "+e1);
			System.exit(-1);
		}

		// If the secret hasn't been assigned
		if (Settings.getSecret() == null) {
			Settings.setSecret(Settings.nextSecret());
		}
		log.info("The secret is : " + Settings.getSecret());

		initiateConnection();
		start();
	}
	
	public void initiateConnection(){
		// make a connection to another server if remote hostname is supplied
		if(Settings.getRemoteHostname()!=null){
			try {
				Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(),Settings.getRemotePort()));

				JSONObject obj = new JSONObject();
				obj.put("command", "AUTHENTICATE");
				obj.put("secret", Settings.getSecret());
				c.writeMsg(obj.toJSONString());
				validatedServerConnections.add(c);

				log.info("Sending authentication request");
			} catch (IOException e) {
				log.error("failed to make connection to "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+" :"+e);
				System.exit(-1);
			}
		}
	}
	
	/*
	 * Processing incoming messages from the connection.
	 * Return true if the connection should close.
	 */
	public synchronized boolean process(Connection con,String msg) {
		JSONParser parser = new JSONParser();
		// The request JSON Object and response JSON Object
		JSONObject reqObj;
		JSONObject resObj = new JSONObject();

		try {
			reqObj = (JSONObject) parser.parse(msg);
		} catch (ParseException e1) {
			responseToInvalidMessage(con, "JSON parse error while parsing message");

			log.error("Invalid JSON object");
			return true;
		}

		if (!reqObj.containsKey("command")) {
			responseToInvalidMessage(con, "The received message did not contain a command");

			log.error("The received message did not contain a command");
			return true;
		}

		String command = (String) reqObj.get("command");
		String username, secret;


		switch (command) {

			// For validated server connections, following commands are acceptable:
			// AUTHENTICATION_FAIL, SERVER_ANNOUNCE, ACTIVITY_BROADCAST, LOCK_REQUEST, LOCK_ALLOW, LOCK_DENY

			case "AUTHENTICATION_FAIL":
				if (!validateServerConnection(con)) return true;

				log.error((String) reqObj.get("info"));
				return true;

			case "SERVER_ANNOUNCE":
				if (!validateServerConnection(con)) return true;

				String rServerId = (String) reqObj.get("id");
				int rServerLoad = ((Number) reqObj.get("load")).intValue();
				String rServerHostname = (String) reqObj.get("hostname");
				int rServerPort = ((Number) reqObj.get("port")).intValue();

				boolean existed = false;
				for (ServerInfo s : allKnownServers) {
					if (s.getServerId().equals(rServerId)) {
						existed = true;
						s.setPort(rServerPort);
						s.setHostname(rServerHostname);
						s.setLoad(rServerLoad);
					}
				}
				if (!existed) {
					allKnownServers.add(new ServerInfo(rServerId, rServerHostname, rServerPort, rServerLoad));
				}

				log.info("Server announcement from " + rServerId + "(" + rServerHostname + ":"
						+ rServerPort + "), " + rServerLoad + " connected client(s)");
				return false;

			case "ACTIVITY_BROADCAST":
				if (!validateServerConnection(con)) return true;

				broadcastMessage(validatedServerConnections, con, reqObj);
				broadcastMessage(validatedClientConnections, con, reqObj);
				return false;

			case "LOCK_REQUEST":
				if (!validateServerConnection(con)) return true;

				username = (String) reqObj.get("username");
				if (!checkUsernameAvailability(username)) {
					resObj.put("command", "LOCK_DENIED");
					resObj.put("info", username + " is already registered with the system");

					con.writeMsg(resObj.toJSONString());
					log.error("this username is registered in this server");
					return false;
				}

				broadcastMessage(validatedServerConnections, con, resObj);
				lockRequestSources.put(username, con);
				pendingRequests.put(username, allKnownServers.size());
				return false;

			case "LOCK_DENIED":
				if (!validateServerConnection(con)) return true;

				username = (String) reqObj.get("username");

				if (pendingRequests.containsKey(username)) {
					pendingRequests.remove(username);
					// if the register request is from a client of this server
					// return register failed
					if (registerRequestSources.containsKey(username)) {
						resObj.put("command", "REGISTER_FAILED");
						resObj.put("info", username + " is already registered with the system");

						registerRequestSources.get(username).writeMsg(resObj.toJSONString());
						log.error("this username is registered in this server");

						registerRequestSources.remove(username);
						return false;
					} else {
						// if not, send back to the source server
						lockRequestSources.get(username).writeMsg(reqObj.toJSONString());
					}

				}

				// If the username has been registered on this server,
				// then remove local username and secret
				registeredUsers.removeIf(user -> user.getUsername().equals(username));

				return false;

			case "LOCK_ALLOWED":
				if (!validateServerConnection(con)) return true;

				username = (String) reqObj.get("username");
				secret = (String) reqObj.get("secret");

				// if the username is still in pending status
				if (pendingRequests.containsKey(username)) {
					pendingRequests.put(username, pendingRequests.get(username) - 1);

					// save this user to this server
					if(checkUsernameAvailability(username)) {
						registeredUsers.add(new UserInfo(username, secret));
					}

					// if all the server responded with lock_allowed
					if (pendingRequests.get(username) <= 0) {
						pendingRequests.remove(username);
						// if the register request is from a client of this server
						if (registerRequestSources.containsKey(username)) {
							resObj.put("command", "REGISTER_SUCCESS");
							resObj.put("info", "register success for " + username);

							validatedClientConnections.add(registerRequestSources.get(username));

							registerRequestSources.get(username).writeMsg(resObj.toJSONString());
							registerRequestSources.remove(username);

							log.info(username + " registered successfully");
						}
					}

					if(lockRequestSources.containsKey(username)) {
						// if it is requested by a server,
						// send back to the server
						lockRequestSources.get(username).writeMsg(reqObj.toJSONString());
					}
				}
				return false;


			// For validated client connections, following commands are acceptable:
			// ACTIVITY_MESSAGE, LOGOUT

			case "ACTIVITY_MESSAGE":
				if (!validateClientConnection(con)) return true;

				// validate the provided credential
				if (!validateUser(reqObj)) {
					resObj.put("command", "AUTHENTICATION_FAIL");
					resObj.put("info", "the supplied secret is incorrect");
					con.writeMsg(resObj.toJSONString());

					log.error("activity message authentication failed");
					return true;
				}

				JSONObject activity = (JSONObject) reqObj.get("activity");
				resObj.put("command", "ACTIVITY_BROADCAST");
				resObj.put("activity", activity);
				broadcastMessage(validatedServerConnections, con, resObj);
				broadcastMessage(validatedClientConnections, null, resObj);

				log.info("broadcast message: " + resObj.toJSONString());
				return false;

			case "LOGOUT":
				// if(!validateClientConnection(con)) return true;
				// just close the connection
				return true;


			// For any connection, following commands are acceptable:
			// AUTHENTICATE, LOGIN, REGISTER

			case "AUTHENTICATE":
				if (validatedServerConnections.contains(con)) {
					responseToInvalidMessage(con, "the server had already authenticated");

					log.error("the server had already authenticated");
					return true;
				}

				secret = (String) reqObj.get("secret");
				// if and only if the secrets match, authenticate success
				if (secret.equals(Settings.getSecret())) {
					log.info("authentication success");
					validatedServerConnections.add(con);
					return false;
				} else {
					reqObj.put("command", "AUTHENTICATION_FAIL");
					reqObj.put("info", "the supplied secret is incorrect: " + secret);
					con.writeMsg(reqObj.toJSONString());
					log.error("authentication failed");
					return true;
				}

			case "LOGIN":
				username = (String) reqObj.get("username");
				if (validateUser(reqObj)) {
					// for a success login attempt
					resObj.put("command", "LOGIN_SUCCESS");
					resObj.put("info", "logged in as " + username);
					con.writeMsg(resObj.toJSONString());

					validatedClientConnections.add(con);
					log.info("logged in as " + username);
				} else {
					// for a failed login attempt
					resObj.put("command", "LOGIN_FAILED");
					resObj.put("info", "attempt to login with wrong secret");
					con.writeMsg(resObj.toJSONString());
					log.error("login failed");
					return true;
				}

				// if the client logged in successfully,
				// we will check whether it needs to be redirected to another server.
				for (ServerInfo s : allKnownServers) {
					if (s.getLoad() < (validatedClientConnections.size() - 2)) {
						// there is a server with a load at least 2 clients less
						resObj.put("command", "REDIRECT");
						resObj.put("hostname", s.getHostname());
						resObj.put("port", s.getPort());
						con.writeMsg(resObj.toJSONString());
						log.info("redirect to another server");
						return true;
					}
				}
				// only if the client is authenticated and won't be redirected,
				// this connection shouldn't be closed.
				return false;


			case "REGISTER":
				if (validatedClientConnections.contains(con)) {
					responseToInvalidMessage(con, "the client has logged in");
					return true;
				}

				username = (String) reqObj.get("username");
				secret = (String) reqObj.get("secret");

				// if the username is available on this server
				if (checkUsernameAvailability(username)) {
					if(allKnownServers.size() > 0) {
						// if there are other servers in this network,
						// we have to check other servers for this username
						resObj.put("command", "LOCK_REQUEST");
						resObj.put("username", username);
						reqObj.put("secret", secret);

						broadcastMessage(validatedServerConnections, con, resObj);
						pendingRequests.put(username, allKnownServers.size());
						registerRequestSources.put(username, con);

						log.info("sending out lock requests");
						return false;
					} else {
						resObj.put("command", "REGISTER_SUCCESS");
						resObj.put("info", "register success for " + username);

						registeredUsers.add(new UserInfo(username, secret));

						validatedClientConnections.add(con);

						log.info(username + " registered successfully");
						return false;
					}
				} else { // if the username is taken
					resObj.put("command", "REGISTER_FAILED");
					resObj.put("info", username + " is already registered with the system");
					con.writeMsg(resObj.toJSONString());
					log.error("this username is registered in this server");
					return true;
				}

			default:
				break;
		}

		return true;
	}
	
	/*
	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con){
		if(!term) {
			connections.remove(con);
			validatedClientConnections.remove(con);
			validatedServerConnections.remove(con);
			con.closeCon();
		}
	}
	
	/*
	 * A new incoming connection has been established, and a reference is returned to it
	 */
	public synchronized Connection incomingConnection(Socket s) throws IOException{
		log.debug("incoming connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		return c;
	}
	
	/*
	 * A new outgoing connection has been established, and a reference is returned to it
	 */
	public synchronized Connection outgoingConnection(Socket s) throws IOException{
		log.debug("outgoing connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		return c;
	}
	
	@Override
	public void run(){
		log.info("using activity interval of "+Settings.getActivityInterval()+" milliseconds");
		while(!term){
			// do something with 5 second intervals in between
			try {
				Thread.sleep(Settings.getActivityInterval());
			} catch (InterruptedException e) {
				log.info("received an interrupt, system is shutting down");
				break;
			}
			if(!term){
				log.info("Sending server announcement");
				term=doActivity();
			}
			
		}
		log.info("closing "+ connections.size()+" connections");
		// clean up
		for(Connection connection : connections){
			connection.closeCon();
		}
		listener.setTerm(true);
	}

	// send server announce
	public boolean doActivity(){
		for(Connection con : validatedServerConnections) {
			JSONObject obj = new JSONObject();
			obj.put("command", "SERVER_ANNOUNCE");
			obj.put("id", serverId);
			obj.put("load", validatedClientConnections.size());
			obj.put("hostname", Settings.getLocalHostname());
			obj.put("port", Settings.getLocalPort());

			con.writeMsg(obj.toJSONString());
		}
		return false;
	}
	
	public final void setTerm(boolean t){
		term=t;
	}

	private boolean validateUser(JSONObject obj) {
		if(!obj.containsKey("username")) {
			return false;
		}
		String username = (String) obj.get("username");
		if(username.equals("anonymous")) {
			return true;
		} else {
			if(!obj.containsKey("secret")) {
				return false;
			}
			String secret = (String) obj.get("secret");
			for(UserInfo user: registeredUsers) {
				if(user.getUsername().equals(username)
						&& user.getSecret().equals(secret)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean validateClientConnection(Connection con) {
		return validatedClientConnections.contains(con);
	}

	private boolean validateServerConnection(Connection con) {
		if(validatedServerConnections.contains(con)) {
			return true;
		} else {
			JSONObject obj = new JSONObject();
			obj.put("command", "AUTHENTICATION_FAIL");
			obj.put("info", "the server is not authenticated");
			con.writeMsg(obj.toJSONString());
			return false;
		}
	}

	private boolean checkUsernameAvailability(String username) {
		for(UserInfo user : registeredUsers) {
			if(user.getUsername().equals(username)) return false;
		}
		return true;
	}

	private void responseToInvalidMessage(Connection con, String msg) {
		JSONObject obj = new JSONObject();
		obj.put("command", "INVALID_MESSAGE");
		obj.put("info", msg);
		con.writeMsg(obj.toJSONString());
	}


	// Broadcast a message to all servers (except for the given connection)
	private void broadcastMessage(ArrayList<Connection> connections, Connection current, JSONObject res) {
		for(Connection con : connections) {
			if(con!=current) {
				con.writeMsg(res.toJSONString());

			}
		}
	}
}
