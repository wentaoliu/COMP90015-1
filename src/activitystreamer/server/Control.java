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

	private class Server {
		public Server(String id, String hostname, int port, int load) {
			this.id = id;
			this.hostname = hostname;
			this.port = port;
			this.load = load;
		}

		public String id;
		public String hostname;
		public int port;
		public int load;
	}

	private static final Logger log = LogManager.getLogger();
	private static ArrayList<Connection> connections;
	private static boolean term=false;
	private static Listener listener;

	private static Map<String, String> users;

	// username and incoming connection
	private static Map<String, Connection> registerRequests;
	// username and outgoing connections
	private static Map<String, ArrayList<Connection>> lockRequests;
	private static ArrayList<Server> servers;
	
	protected static Control control = null;
	
	public static Control getInstance() {
		if(control==null){
			control=new Control();
		} 
		return control;
	}
	
	public Control() {
		// initialize the connections array
		connections = new ArrayList<Connection>();
		// credentials
		users = new HashMap<>();
		registerRequests = new HashMap<>();
		lockRequests = new HashMap<>();
		servers = new ArrayList<>();
		// start a listener
		try {
			listener = new Listener();
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: "+e1);
			System.exit(-1);
		}

		initiateConnection();
		start();
	}
	
	public void initiateConnection(){
		// make a connection to another server if remote hostname is supplied
		if(Settings.getRemoteHostname()!=null){
			try {
				Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(),Settings.getRemotePort()));

				c.writeMsg("{\"command\":\"AUTHENTICATE\",\"secret\":\"" + Settings.getSecret() + "\"}");
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
	public synchronized boolean process(Connection con,String msg){
		JSONParser parser = new JSONParser();
		JSONObject obj;
		String command;
		String secret;
		String username;
		String info;
		String id;
		String hostname;
		ArrayList<Connection> outgoings;

		try {
			obj = (JSONObject) parser.parse(msg);
		} catch (ParseException e1) {
			con.writeMsg("{\"command\" : \"INVALID_MESSAGE\"," +
					"\"info\" : \"JSON parse error while parsing message\"}");
			log.error("invalid JSON object entered into input text field, data not sent");
			return true;
		}

		command = (String) obj.get("command");

		if(command.isEmpty()) {
			con.writeMsg("{\"command\" : \"INVALID_MESSAGE\"," +
					"\"info\" : \"the received message did not contain a command\"}");
			log.error("the received message did not contain a command");
			return true;
		}

		switch (command) {
			case "AUTHENTICATE":
				if(con.isAuthenticatedServer()) {
					con.writeMsg("{\"command\" : \"INVALID_MESSAGE\"," +
							"\"info\" : \"the server had already authenticated\"}");
					log.error("the server had already authenticated");
					break;
				}

				secret = (String) obj.get("secret");
				// if and only if the secrets match, authenticate success
				if(secret.equals(Settings.getSecret())) {
					log.debug("authentication success");
					con.authenticateSever(true);
					return false;
				} else {
					con.writeMsg("{\"command\" : \"AUTHENTICATION_FAIL\"," +
							"\"info\" : \"the supplied secret is incorrect: " + secret + "\"}");
				}
				break;

			case "INVALID_MESSAGE":
			case "AUTHENTICATION_FAIL":
				info = (String) obj.get("info");
				log.error(info);
				break;

			case "LOGIN":
				boolean valid = validateUser(obj);
				username = (String) obj.get("username");
				if(valid) {
					con.writeMsg("{\"command\" : \"LOGIN_SUCCESS\"," +
							"\"info\" : \"logged in as " + username + "\"}");
					con.authenticateClient(true);
					log.debug("logged in as " + username);
				} else {
					con.writeMsg("{\"command\" : \"LOGIN_FAILED\",\n" +
						"\"info\" : \"attempt to login with wrong secret\"}");
					log.error("login failed");
				}

				// if the client logged in successfully,
				// we will check whether it needs to be redirected to another server.
				if(con.isAuthenticatedClient()) {
					for(Server s : servers) {
						if(s.load < (numberOfClients() - 2)) {
							// there is a server with a load at least 2 clients less
							con.writeMsg("{\"command\" : \"REDIRECT\"," +
									"\"hostname\" : \"" + s.hostname + "\"," +
									"\"port\" : " + s.port + "}");
							log.debug("redirect to another server");
							return true;
						}
					}
					// only if the client is authenticated and won't be redirected,
					// this connection shouldn't be closed.
					return false;
				}
				break;

			case "LOGOUT":
				// just close the connection
				break;

			case "ACTIVITY_MESSAGE":
				boolean authenticated = validateUser(obj);
				// validate the provided credential
				if(!authenticated) {
					con.writeMsg("{\"command\" : \"AUTHENTICATION_FAIL\"," +
							"\"info\" : \"the supplied secret is incorrect\"}");
					log.error("activity message authentication failed");
					break;
				}
				for(Connection c : connections) {
					//if (c !=con && (c.isAuthenticatedServer() || c.isAuthenticatedClient())) {
					if(c.isAuthenticatedServer() || c.isAuthenticatedClient()) {
						Object activity = obj.get("activity");

						c.writeMsg("{\"command\":\"ACTIVITY_BROADCAST\", \"activity\":"+
							((JSONObject) activity).toJSONString() + "}");
						log.debug("sending message");
					}
				}
				log.debug(msg);
				return false;

			case "SERVER_ANNOUNCE":
				if(con.isAuthenticatedServer()) {
					id = (String) obj.get("id");
					int load = ((Long) obj.get("load")).intValue();
					hostname = (String) obj.get("hostname");
					int port = ((Long) obj.get("port")).intValue();

					boolean existed = false;
					for(Server s: servers) {
						if(s.id.equals(id)) {
							existed = true;
							s.port = port;
							s.hostname = hostname;
							s.load = load;
						}
					}
					if(!existed) {
						servers.add(new Server(id, hostname, port, load));
					}

					log.debug("Server announcement from " + id + "(" + hostname + ":" + port + "), "
							+ load + " connected client(s)" );
					return false;
				} else {
					con.writeMsg("{\"command\" : \"INVALID_MESSAGE\"," +
							"\"info\" : \"the server has not been authenticated\"}");
					log.error("the server not authenticated");
				}
				break;

			case "ACTIVITY_BROADCAST":
				if(con.isAuthenticatedServer()) {
					for(Connection c : connections) {
						if (c != con && (c.isAuthenticatedServer() || c.isAuthenticatedClient())) {
							c.writeMsg(msg);
							log.debug("sending message");
						}
					}
					return false;
				} else {
					con.writeMsg("{\"command\" : \"INVALID_MESSAGE\"," +
							"\"info\" : \"the server has not authenticated\"}");
					log.error("the server not authenticated");
				}
				break;

			case "REGISTER":
				if(con.isAuthenticatedClient()) {
					con.writeMsg("{\"command\" : \"INVALID_MESSAGE\"," +
							"\"info\" : \"the client has logged in\"}");
					break;
				}

				username = (String) obj.get("username");
				secret = (String) obj.get("secret");

				if(users.containsKey(username)) {
					con.writeMsg("{\"command\" : \"REGISTER_FAILED\"," +
							"\"info\" : \"" + username + " is already registered with the system\"}");
					log.error("this username is registered in this server");
					break;
				}

				outgoings = new ArrayList<>();
				for(Connection c : connections) {
					if(c!=con && c.isAuthenticatedServer()) {
						con.writeMsg("{\"command\" : \"LOCK_REQUEST\",\n" +
								"\"username\" : \"" + username + "\"," +
								"\"secret\" : \"" + secret + "\"}");
						outgoings.add(c);
					}
				}
				lockRequests.put(username, outgoings);
				registerRequests.put(username, con);
				return false;
				//break;

			case "LOCK_REQUEST":
				if(!con.isAuthenticatedServer()) {
					con.writeMsg("{\"command\" : \"INVALID_MESSAGE\"," +
							"\"info\" : \"the server has not been authenticated\"}");
					break;
				}

				username = (String) obj.get("username");
				if(users.containsKey(username)) {
					con.writeMsg("{\"command\" : \"LOCK_DENIED\"," +
							"\"info\" : \"" + username + " is already registered with the system\"}");
					log.error("this username is registered in this server");
					return false;
				}

				outgoings = new ArrayList<>();
				for(Connection c : connections) {
					if(c!=con && c.isAuthenticatedServer()) {
						con.writeMsg(msg);
						outgoings.add(c);
					}
				}
				lockRequests.put(username, outgoings);
				return false;
				//break;

			case "LOCK_DENIED":
				// from unauthenticated server
				if(!con.isAuthenticatedServer()) {
					con.writeMsg("{\"command\" : \"INVALID_MESSAGE\"," +
							"\"info\" : \"the server has not been authenticated\"}");
					break;
				}
				username = (String) obj.get("username");

				// if the register request is from a client of this server
				// return register failed
				if(registerRequests.containsKey(username)) {
					registerRequests.get(username).writeMsg("{\"command\" : \"REGISTER_FAILED\"," +
							"\"info\" : \"" + username + " is already registered with the system\"}");
					log.error("this username is registered in this server");
					registerRequests.remove(username);
					return false;
				}

				// if not, broadcast to all other servers
				if(lockRequests.containsKey(username)) {
					for(Connection c:connections) {
						if(c!=con && c.isAuthenticatedServer()) c.writeMsg(msg);
					}
					lockRequests.remove(username);
				}
				return false;
				//break;

			case "LOCK_ALLOWED":
				// from unauthenticated server
				if(!con.isAuthenticatedServer()) {
					con.writeMsg("{\"command\" : \"INVALID_MESSAGE\"," +
							"\"info\" : \"the server has not been authenticated\"}");
					break;
				}

				username = (String) obj.get("username");

				if(lockRequests.containsKey(username)) {
					lockRequests.get(username).remove(con);
					// if all the server responded with lock_allowed
					if(lockRequests.get(username).isEmpty()) {
						// if the register request is from a client of this server
						if(registerRequests.containsKey(username)) {
							registerRequests.get(username).writeMsg("{\"command\" : \"REGISTER_SUCCESS\"," +
									"\"info\" : \"register success for " + username + "\"}");
							log.error("this username is registered in this server");
							registerRequests.remove(username);
							return false;
						} else {
							// if not, broadcast to all other servers
							for(Connection c : connections) {
								if(c!=con && c.isAuthenticatedServer()) c.writeMsg(msg);
							}
							lockRequests.remove(username);
							return false;
						}

					}
				}
				break;

			default:
				break;
		}

		return true;
	}
	
	/*
	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con){
		if(!term) connections.remove(con);
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
				log.debug("doing activity");
				term=doActivity();
			}
			
		}
		log.info("closing "+connections.size()+" connections");
		// clean up
		for(Connection connection : connections){
			connection.closeCon();
		}
		listener.setTerm(true);
	}

	// send server announce
	public boolean doActivity(){
		//TODO generate id in Settings
		for(Connection con : connections) {
			if(con.isAuthenticatedServer()) {
				con.writeMsg("{\"command\" : \"SERVER_ANNOUNCE\"," +
						"\"id\" : \"" + Settings.getId() + "\"," +
						"\"load\" : " + numberOfClients() + "," +
						"\"hostname\" : \"" + Settings.getLocalHostname() + "\"," +
						"\"port\" : " + Settings.getLocalPort() + "}");
			}
		}
		return false;
	}
	
	public final void setTerm(boolean t){
		term=t;
	}
	
	public final ArrayList<Connection> getConnections() {
		return connections;
	}

	private int numberOfClients() {
		int num = 0;
		for(Connection c : connections) {
			if(c.isAuthenticatedClient()) num++;
		}
		return num;
	}

	private boolean validateUser(JSONObject obj) {
		String username = (String) obj.get("username");
		if(username.equals("anonymous")) {
			return true;
		} else {
			String secret = (String) obj.get("secret");
			if(users.containsKey(username)) {
				String storedSecret = users.get(username);
				if(secret.equals(storedSecret)) {
					return true;
				}
			}
		}
		return false;
	}
}
