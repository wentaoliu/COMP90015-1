package activitystreamer.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;

import activitystreamer.Client;
import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

@SuppressWarnings("serial")
public class TextFrame extends JFrame implements ActionListener {
	private static final Logger log = LogManager.getLogger();

	private JSONParser parser = new JSONParser();

	// The connection is open or not
	private boolean open;


	// UI components
	private JButton btnAnonymous, btnLogin, btnLogout, btnSend, btnRegister;
	private JTextArea inputText, outputText;
	private JLabel lblPort, lblIcon, lblHostname, lblUsername, lblSecret;
	private JPanel panelMain, panelLeft, panelInput, panelOutput;
	private JScrollPane jScrollPane1, jScrollPane2;
	private JTextField txtHostname, txtPort, txtUsername, txtSecret;
	private Font fntMain;
	private Color clrFore, clrBack, clrTheme;

	public TextFrame(boolean open) {
		initComponents();
		onlineMode(open);
	}

	private void initComponents() {
		setTitle("FlyMule ActivityStreamer");

		panelMain = new JPanel();
		panelLeft = new JPanel();
		panelInput = new JPanel();
		panelOutput = new JPanel();
		jScrollPane1 = new JScrollPane();
		jScrollPane2 = new JScrollPane();

		// Lables
		lblIcon = new JLabel();
		lblHostname = new JLabel();
		lblPort = new JLabel();
		lblUsername = new JLabel();
		lblSecret = new JLabel();
		// TextField
		txtHostname = new JTextField();
		txtPort = new JTextField();
		txtUsername = new JTextField();
		txtSecret = new JTextField();
		// Buttons
		btnLogin = new JButton();
		btnRegister = new JButton();
		btnAnonymous = new JButton();
		btnSend = new JButton();
		btnLogout = new JButton();
		// TextAreas
		inputText = new JTextArea();
		outputText = new JTextArea();
		// Fonts
		fntMain = new Font("Calibri", 0, 18);
		// Colors
		clrTheme = new Color(0, 124, 204);
		clrFore = new Color(255, 255, 255);
		clrBack = new Color(0, 0, 0);

		panelMain.setBackground(clrFore);
		panelLeft.setBackground(clrTheme);

		lblIcon.setIcon(new ImageIcon(getClass().getResource("/logo.png")));

		lblHostname.setFont(fntMain);
		lblHostname.setForeground(clrFore);
		lblHostname.setText("Hostname");

		txtHostname.setFont(fntMain);
		txtHostname.setForeground(clrBack);
		txtHostname.addActionListener(this);

		lblPort.setFont(fntMain);
		lblPort.setForeground(clrFore);
		lblPort.setText("Port");

		txtPort.setFont(fntMain);
		txtPort.setForeground(new java.awt.Color(51, 51, 51));

		lblUsername.setFont(fntMain);
		lblUsername.setForeground(clrFore);
		lblUsername.setText("Username");

		lblSecret.setFont(fntMain);
		lblSecret.setForeground(clrFore);
		lblSecret.setText("Secret");

		btnLogin.setFont(fntMain);
		btnLogin.setText("Login");
		btnLogin.addActionListener(this);

		btnRegister.setFont(fntMain);
		btnRegister.setText("Register");
		btnRegister.addActionListener(this);

		btnAnonymous.setFont(fntMain);
		btnAnonymous.setText("Login as Anonymous");
		btnAnonymous.addActionListener(this);

		btnSend.setFont(fntMain);
		btnSend.setText("Send");
		btnSend.addActionListener(this);

		btnLogout.setFont(fntMain);
		btnLogout.setText("Logout");
		btnLogout.addActionListener(this);

		GroupLayout jPanel2Layout = new GroupLayout(panelLeft);
		panelLeft.setLayout(jPanel2Layout);
		jPanel2Layout.setHorizontalGroup(
				jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addGroup(jPanel2Layout.createSequentialGroup()
								.addGroup(jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
										.addGroup(jPanel2Layout.createSequentialGroup()
												.addGap(20)
												.addGroup(jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
														.addComponent(lblSecret)
														.addComponent(lblUsername)
														.addComponent(lblHostname)
														.addComponent(txtHostname)
														.addComponent(lblPort)
														.addComponent(txtPort)
														.addComponent(txtUsername)
														.addComponent(txtSecret)
														.addGroup(jPanel2Layout.createSequentialGroup()
																.addComponent(btnLogin, GroupLayout.PREFERRED_SIZE, 95, GroupLayout.PREFERRED_SIZE)
																.addGap(10)
																.addComponent(btnRegister, GroupLayout.PREFERRED_SIZE, 94, GroupLayout.PREFERRED_SIZE)
																.addGap(10)
																.addComponent(btnAnonymous, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
										.addGroup(jPanel2Layout.createSequentialGroup()
												.addGap(96, 96, 96)
												.addComponent(lblIcon, GroupLayout.PREFERRED_SIZE, 256, GroupLayout.PREFERRED_SIZE)))
								.addContainerGap(20, Short.MAX_VALUE))
		);
		jPanel2Layout.setVerticalGroup(
				jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addGroup(jPanel2Layout.createSequentialGroup()
								.addGap(27, 27, 27)
								.addComponent(lblIcon, GroupLayout.PREFERRED_SIZE, 256, GroupLayout.PREFERRED_SIZE)
								.addGap(41, 41, 41)
								.addComponent(lblHostname)
								.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
								.addComponent(txtHostname, GroupLayout.PREFERRED_SIZE, 38, GroupLayout.PREFERRED_SIZE)
								.addGap(18, 18, 18)
								.addComponent(lblPort)
								.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
								.addComponent(txtPort, GroupLayout.PREFERRED_SIZE, 38, GroupLayout.PREFERRED_SIZE)
								.addGap(18, 18, 18)
								.addComponent(lblUsername)
								.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
								.addComponent(txtUsername, GroupLayout.PREFERRED_SIZE, 38, GroupLayout.PREFERRED_SIZE)
								.addGap(18, 18, 18)
								.addComponent(lblSecret)
								.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
								.addComponent(txtSecret, GroupLayout.PREFERRED_SIZE, 38, GroupLayout.PREFERRED_SIZE)
								.addGroup(jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
										.addGroup(jPanel2Layout.createSequentialGroup()
												.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
												.addComponent(btnLogin, GroupLayout.PREFERRED_SIZE, 43, GroupLayout.PREFERRED_SIZE))
										.addGroup(jPanel2Layout.createSequentialGroup()
												.addGap(34, 34, 34)
												.addGroup(jPanel2Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
														.addComponent(btnAnonymous, GroupLayout.PREFERRED_SIZE, 43, GroupLayout.PREFERRED_SIZE)
														.addComponent(btnRegister, GroupLayout.PREFERRED_SIZE, 43, GroupLayout.PREFERRED_SIZE))))
								.addGap(63, 63, 63))
		);

		panelInput.setBackground(clrFore);
		panelInput.setBorder(BorderFactory.createTitledBorder(null, "JSON input, to send to server",
				javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, fntMain));

		inputText.setColumns(20);
		inputText.setRows(5);
		jScrollPane2.setViewportView(inputText);



		GroupLayout jPanel4Layout = new GroupLayout(panelInput);
		panelInput.setLayout(jPanel4Layout);
		jPanel4Layout.setHorizontalGroup(
				jPanel4Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addGroup(jPanel4Layout.createSequentialGroup()
								.addContainerGap()
								.addGroup(jPanel4Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
										.addComponent(jScrollPane2)
										.addGroup(jPanel4Layout.createSequentialGroup()
												.addGap(0, 223, Short.MAX_VALUE)
												.addComponent(btnSend, GroupLayout.PREFERRED_SIZE, 78, GroupLayout.PREFERRED_SIZE)
												.addGap(31, 31, 31)
												.addComponent(btnLogout, GroupLayout.PREFERRED_SIZE, 124, GroupLayout.PREFERRED_SIZE)))
								.addContainerGap())
		);
		jPanel4Layout.setVerticalGroup(
				jPanel4Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addGroup(jPanel4Layout.createSequentialGroup()
								.addComponent(jScrollPane2, GroupLayout.PREFERRED_SIZE, 355, GroupLayout.PREFERRED_SIZE)
								.addGap(18, 18, 18)
								.addGroup(jPanel4Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
										.addComponent(btnLogout, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
										.addComponent(btnSend, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
								.addGap(17, 17, 17))
		);

		panelOutput.setBackground(clrFore);
		panelOutput.setBorder(BorderFactory.createTitledBorder(null, "JSON output, received from server",
				javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, fntMain));

		outputText.setColumns(20);
		outputText.setRows(5);
		jScrollPane1.setViewportView(outputText);

		GroupLayout jPanel5Layout = new GroupLayout(panelOutput);
		panelOutput.setLayout(jPanel5Layout);
		jPanel5Layout.setHorizontalGroup(
				jPanel5Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addGroup(jPanel5Layout.createSequentialGroup()
								.addContainerGap()
								.addComponent(jScrollPane1)
								.addContainerGap())
		);
		jPanel5Layout.setVerticalGroup(
				jPanel5Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addGroup(jPanel5Layout.createSequentialGroup()
								.addContainerGap()
								.addComponent(jScrollPane1, GroupLayout.PREFERRED_SIZE, 201, GroupLayout.PREFERRED_SIZE)
								.addContainerGap(21, Short.MAX_VALUE))
		);

		GroupLayout panelMainLayout = new GroupLayout(panelMain);
		panelMain.setLayout(panelMainLayout);
		panelMainLayout.setHorizontalGroup(
				panelMainLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addGroup(panelMainLayout.createSequentialGroup()
								.addComponent(panelLeft, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
								.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
								.addGroup(panelMainLayout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
										.addComponent(panelInput, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
										.addComponent(panelOutput, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
								.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
		);
		panelMainLayout.setVerticalGroup(
				panelMainLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
						.addComponent(panelLeft, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
						.addGroup(panelMainLayout.createSequentialGroup()
								.addComponent(panelInput, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
								.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
								.addComponent(panelOutput, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
								.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
		);

		add(panelMain);


		setSize(970, 770);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		setResizable(false);

		setVisible(true);
	}

	public void setOutputText(final JSONObject obj){
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonParser jp = new JsonParser();
		JsonElement je = jp.parse(obj.toJSONString());
		String prettyJsonString = gson.toJson(je);
		outputText.setText(prettyJsonString);
		outputText.revalidate();
		outputText.repaint();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource()==btnSend){
			String msg = inputText.getText().trim().replaceAll("\r","").replaceAll("\n","").replaceAll("\t", "");
			JSONObject obj;
			try {
				obj = (JSONObject) parser.parse(msg);
				ClientSolution.getInstance().sendActivityObject(obj);
			} catch (ParseException e1) {
				log.error("invalid JSON object entered into input text field, data not sent");
			}

		} else if(e.getSource() == btnAnonymous){
			if(!txtHostname.getText().isEmpty()) {
				Settings.setRemoteHostname(txtHostname.getText());
			}
			if(!txtPort.getText().isEmpty()) {
				Settings.setRemotePort(Integer.parseInt(txtPort.getText()));
			}

			Settings.setUsername("anonymous");
			Settings.setSecret(null);
			ClientSolution.getInstance().initiateConnection();
			ClientSolution.getInstance().login();
		} else if(e.getSource() == btnLogin){
			if(!txtHostname.getText().isEmpty()) {
				Settings.setRemoteHostname(txtHostname.getText());
			}
			if(!txtPort.getText().isEmpty()) {
				Settings.setRemotePort(Integer.parseInt(txtPort.getText()));
			}
			Settings.setUsername(txtUsername.getText());
			Settings.setSecret(txtSecret.getText());
			ClientSolution.getInstance().initiateConnection();
			ClientSolution.getInstance().login();
		} else if(e.getSource() == btnRegister){
			if(!txtHostname.getText().isEmpty()) {
				Settings.setRemoteHostname(txtHostname.getText());
			}
			if(!txtPort.getText().isEmpty()) {
				Settings.setRemotePort(Integer.parseInt(txtPort.getText()));
			}
			Settings.setUsername(txtUsername.getText());
			Settings.setSecret(txtSecret.getText());
			ClientSolution.getInstance().initiateConnection();
			ClientSolution.getInstance().register();
		} else if(e.getSource()==btnLogout){
			log.debug("connection closed");
			onlineMode(false);
			ClientSolution.getInstance().disconnect();

		}
	}

	public void onlineMode(boolean online){
		txtHostname.setEnabled(!online);
		txtPort.setEnabled(!online);
		txtUsername.setEnabled(!online);
		txtSecret.setEnabled(!online);
		btnLogin.setEnabled(!online);
		btnRegister.setEnabled(!online);
		btnAnonymous.setEnabled(!online);

		inputText.setEnabled(online);
		outputText.setEnabled(online);
		btnSend.setEnabled(online);
		btnLogout.setEnabled(online);
	}


}
