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
	private boolean open = false;

	
	public static ClientSolution getInstance(){
		if(clientSolution==null){
			clientSolution = new ClientSolution();
		}
		return clientSolution;
	}
	
	public ClientSolution(){
		boolean connected = false;
		if(Settings.getRemoteHostname() != null) {
			if(Settings.getUsername().equals("anonymous")
					|| Settings.getSecret()!=null) {
				connected = initiateConnection();
			}
		}
		textFrame = new TextFrame(connected);

	}
	
	
	
	public boolean initiateConnection() {
		socket = null;
		try{
			socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			inreader = new BufferedReader(new InputStreamReader(in));
			outwriter = new PrintWriter(out, true);

			open = true;
			log.info("New Connection Established");

			textFrame.onlineMode(true);
			start();
			return true;
		} catch (IOException e){
			log.fatal("Connection Failed:" + e.getMessage());
			System.exit(-1);
		}
		return false;
	}

	public void login() {
		// try to login
		JSONObject obj = new JSONObject();
		obj.put("command", "LOGIN");
		obj.put("username", Settings.getUsername());
		if(!Settings.getUsername().equals("anonymous")) {
			obj.put("secret", Settings.getSecret());
		}
		sendActivityObject(obj);
		log.debug("Sending login request");
	}

	public void register() {
		// try to login
		JSONObject obj = new JSONObject();
		obj.put("command", "REGISTER");
		obj.put("username", Settings.getUsername());
		obj.put("secret", Settings.getSecret());

		sendActivityObject(obj);
		log.debug("Sending register request");
	}

	@SuppressWarnings("unchecked")
	public void sendActivityObject(JSONObject activityObj){
		if(open){
			outwriter.println(activityObj);
			outwriter.flush();
		}
	}
	
	
	public void disconnect(){
		JSONObject obj = new JSONObject();
		obj.put("command", "LOGOUT");
		sendActivityObject(obj);

		if(socket != null) try {
			inreader.close();
			out.close();
			socket.close();
			socket = null;
			open = false;
			term = true;
		}catch (IOException e){
			log.error("close:" + e.getMessage());
		}

		textFrame.setVisible(false);
		textFrame.dispose();
	}
	
	
	public void run(){
		try {
			String data;
			while(!term && (data = inreader.readLine())!=null){
				term = process(data);
			}
			log.debug("connection closed to "+Settings.socketAddress(socket));
			disconnect();
			in.close();
		} catch (IOException e) {
			log.error("connection "+Settings.socketAddress(socket)+" closed with exception: "+e);
			disconnect();
			}
		open=false;
	}

	private boolean process(String msg) {
		JSONParser parser = new JSONParser();
		JSONObject obj = null;
		String command = null;
		try {
			obj = (JSONObject) parser.parse(msg);
			command = (String) obj.get("command");

		} catch (ParseException e1) {
			// TODO
			log.error("invalid JSON object entered into input text field, data not sent");
		}

		switch (command) {
			case "REDIRECT":
				disconnect();
				log.debug("Current connection is closing...redirecting to a new server");

				String hostname = (String) obj.get("hostname");
				long port = (long) obj.get("port");

				Settings.setRemoteHostname(hostname);
				Settings.setRemotePort((int) port);

				initiateConnection();
				if(open) return false;
				break;
			case "LOGIN_SUCCESS":
				log.debug("login success");
				return false;
			case "LOGIN_FAILED":
				log.error("login failed");
				break;
			case "INVALID_MESSAGE":
				break;
			case "REGISTER_SUCCESS":
				return false;
			case "REGISTER_FAILED":
				break;
			case "ACTIVITY_BROADCAST":
				log.debug("activity: " + obj);
				textFrame.setOutputText(obj);
				return false;
			default:
				break;
		}
		return true;
	}
	
}
