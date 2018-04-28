package activitystreamer.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;

public class ClientSolution extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSolution clientSolution;
	private TextFrame textFrame;

	private Socket socket;
	private DataInputStream in;
	private DataOutputStream out;
	private BufferedReader inreader;
	private PrintWriter outwriter;

	private boolean term = false;
	
	public static ClientSolution getInstance(){
		if(clientSolution==null){
			clientSolution = new ClientSolution();
		}
		return clientSolution;
	}
	
	public ClientSolution(){
		try{
			socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			inreader = new BufferedReader(new InputStreamReader(in));
			outwriter = new PrintWriter(out, true);

			log.info("New Connection Established");

			// Try to login or register
			initiate();
		} catch (IOException e){
			log.fatal("Connection Failed:" + e.getMessage());
			System.exit(-1);
		}

		textFrame = new TextFrame();
		start();
	}
	
	
	// Try to login or register
	private void initiate() {
		if(Settings.getUsername().equals("anonymous") ||
				(!Settings.getUsername().equals("anonymous")
					&& Settings.getSecret() != null)) {
			// If the username hasn't been assigned by the command line argument,
			// OR
			// If both the username and secret have been assigned,
			// try to login as anonymous or with the username/secret pair.
			login();
		} else {
			// Only the username is assigned,
			// then attempt to register this username
			// the secret is generated by this client.
			Settings.setSecret(Settings.nextSecret());
			register();
		}
	}

	// send login request
	private void login() {
		// try to login
		JSONObject obj = new JSONObject();
		obj.put("command", "LOGIN");
		obj.put("username", Settings.getUsername());
		if(!Settings.getUsername().equals("anonymous")) {
			obj.put("secret", Settings.getSecret());
		}
		writeJSON(obj);
		log.debug("Try to login in as: " + Settings.getUsername());
	}

	// send register request
	private void register() {
		// try to login
		JSONObject obj = new JSONObject();
		obj.put("command", "REGISTER");
		obj.put("username", Settings.getUsername());
		obj.put("secret", Settings.getSecret());

		writeJSON(obj);
		log.info("Registering username: " + Settings.getUsername()
				+ " with secret: " + Settings.getSecret());
	}

	@SuppressWarnings("unchecked")
	public void sendActivityObject(JSONObject activityObj){
		// build the JSON message object
		JSONObject obj = new JSONObject();
		obj.put("command", "ACTIVITY_MESSAGE");
		obj.put("username", Settings.getUsername());
		if(!Settings.getUsername().equals("anonymous")) {
			obj.put("secret", Settings.getSecret());
		}
		obj.put("activity", activityObj);
		log.info("sending activity: " + obj);
		writeJSON(obj);
	}

	public void writeJSON(JSONObject obj) {
		outwriter.println(obj);
		outwriter.flush();
	}
	
	// disconnect current connection
	// send logout command
	public void disconnect(){
		JSONObject obj = new JSONObject();
		obj.put("command", "LOGOUT");
		writeJSON(obj);

		if(socket != null) try {
			in.close();
			inreader.close();
			out.close();
			outwriter.close();
			socket.close();
		}catch (IOException e){
			log.error("close:" + e.getMessage());
		}

		textFrame.setVisible(false);
		textFrame.dispose();
		System.exit(0);
	}
	
	
	public void run(){
		try {
			String data;
			while(!term){
				data = inreader.readLine();
				if(data==null) continue;
				term = process(data);
			}
			log.debug("connection closed to "+Settings.socketAddress(socket));
			disconnect();
		} catch (IOException e) {
			log.error("connection "+Settings.socketAddress(socket)+" closed with exception: "+e);
			disconnect();
		}
	}

	/*
	 * Processing incoming messages from the connection.
	 * Return true if the connection should close.
	 */
	private boolean process(String msg) {
		JSONParser parser = new JSONParser();
		JSONObject obj;
		String command;
		try {
			obj = (JSONObject) parser.parse(msg);
			command = (String) obj.get("command");

		} catch (ParseException e1) {
			log.error("invalid JSON object entered into input text field, data not sent");
			return true;
		}



		switch (command) {
			case "REDIRECT":
				log.info("Current connection is closing.");

				try {
					String hostname = (String) obj.get("hostname");
					Integer port = ((Number) obj.get("port")).intValue();

					// update the hostname and port number
					Settings.setRemoteHostname(hostname);
					Settings.setRemotePort(port);

					// close current connection
					socket.close();
					// connect to the new server
					socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
					in = new DataInputStream(socket.getInputStream());
					inreader = new BufferedReader(new InputStreamReader(in));
					out = new DataOutputStream(socket.getOutputStream());
					outwriter = new PrintWriter(out, true);

					log.info("Redirect to another server");
					// try to login
					login();
				} catch (Exception e) {
					log.error("Error occurred in redirection: " + e);
					return true;
				}

				return false;

			case "LOGIN_SUCCESS":
				log.info("login success");
				return false;

			case "LOGIN_FAILED":
				log.error("login failed");
				break;

			case "INVALID_MESSAGE":
				log.error("Invalid message!");
				break;

			case "REGISTER_SUCCESS":
				log.info("Register success! Please remember your secret: " + Settings.getSecret());
				return false;

			case "REGISTER_FAILED":
				log.error("Register failed!");
				break;

			case "ACTIVITY_BROADCAST":
				log.debug("activity: " + obj);

				JSONObject activity = (JSONObject) obj.get("activity");
				textFrame.setOutputText(activity);
				return false;

			default:
				break;
		}
		return true;
	}
	
}
