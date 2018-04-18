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

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSkeleton clientSolution;
	private TextFrame textFrame;

	private Socket socket;
	private DataInputStream in;
	private DataOutputStream out;
	private BufferedReader inreader;
	private PrintWriter outwriter;

	private boolean term = false;
	private boolean open = false;

	
	public static ClientSkeleton getInstance(){
		if(clientSolution==null){
			clientSolution = new ClientSkeleton();
		}
		return clientSolution;
	}
	
	public ClientSkeleton(){
		initiateConnection();
		
		textFrame = new TextFrame();
		start();
	}
	
	
	
	public void initiateConnection() {
		socket = null;
		try{
			socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			inreader = new BufferedReader(new InputStreamReader(in));
			outwriter = new PrintWriter(out, true);

			open = true;
			log.debug("New Connection Established");

			// try to login
			JSONObject obj = new JSONObject();
			obj.put("command", "LOGIN");
			obj.put("username", Settings.getUsername());
			if(!Settings.getUsername().equals("anonymous")) {
				obj.put("secret", Settings.getSecret());
			}
			sendActivityObject(obj);
		} catch (IOException e){
			log.fatal("Connection Failed:" + e.getMessage());
			System.exit(-1);
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public void sendActivityObject(JSONObject activityObj){
		if(open){
			outwriter.println(activityObj);
			outwriter.flush();
		}
	}
	
	
	public void disconnect(){
		if(socket != null) try {
			inreader.close();
			out.close();
			socket.close();
			socket = null;
			open = false;
		}catch (IOException e){
			log.error("close:" + e.getMessage());
		}
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
		log.debug(msg);

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
				log.debug(obj);
				textFrame.setOutputText(obj);
				return false;
			default:
				break;
		}
		return true;
	}
	
}
