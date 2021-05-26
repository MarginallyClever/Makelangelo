package com.marginallyclever.makelangelo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import com.marginallyclever.communications.ConnectionManager;
import com.marginallyclever.communications.NetworkConnection;
import com.marginallyclever.core.log.Log;
import com.marginallyclever.core.select.SelectButton;
import com.marginallyclever.core.select.SelectPanel;
import com.marginallyclever.core.CommandLineOptions;
import com.marginallyclever.core.Translator;
import com.marginallyclever.makelangelo.DrivePlotterGUI;
import com.marginallyclever.makelangelo.nodes.LoadFile;
import com.marginallyclever.makelangelo.plotter.Makelangelo5;
import com.marginallyclever.makelangelo.plotter.Plotter;
import com.marginallyclever.makelangelo.plotter.PlotterListener;

/**
 * Control panel for connecting to and driving a {@Plotter}.
 * @author Dan Royer
 * @author Peter Colapietro
 * @since 7.1.4
 */
public class DrivePlotterGUI implements ActionListener, PlotterListener {
	// the robot being controlled
	private Plotter myPlotter;
	// the recording to play
	private RobotController myController;
	
	// the top-most UX element
	private Frame parentFrame;
	private JPanel myPanel;

	// connect menu
	private SelectPanel connectionPanel;
	private SelectButton buttonConnect;
	
	// jog buttons
	private JButton buttonLeftIn;
	private JButton buttonLeftOut;
	private JButton buttonRightIn;
	private JButton buttonRightOut;

	// driving controls
	private JButton down100,down10,down1,up1,up10,up100;
	private JButton left100,left10,left1,right1,right10,right100;
	private JButton setHome,goHome,findHome;
	private JButton penUp,penDown;
	private JButton toggleEngageMotor;
	private JButton toggleDisengageMotor;

	// whole-drawing controls
    private JButton buttonStart, buttonStartAt, buttonPause, buttonHalt;

	private boolean isConnected;
	
	// progress bar, line count, time estimate 
	public StatusBar statusBar;

	/**
	 * @param plotter
	 */
	public DrivePlotterGUI(RobotController controller) {
		myController = controller;
		myPlotter = controller.getPlotter();
	}

	public void run(Frame parent) {
		parentFrame = parent;
		
		JDialog dialog = new JDialog(parent,Translator.get("DrivePlotterGUI.title"), true);
        dialog.setLocation(parent.getLocation());

		myPanel = new JPanel(new GridBagLayout());

		GridBagConstraints con1 = new GridBagConstraints();
		con1.gridx = 0;
		con1.gridy = 0;
		con1.weightx = 1;
		con1.weighty = 0;
		con1.fill = GridBagConstraints.HORIZONTAL;
		con1.anchor = GridBagConstraints.NORTHWEST;

		myPanel.add(createConnectSubPanel(), con1);
		con1.gridy++;
		
		myPanel.add(createJogMotorsPanel(myPlotter),con1);	con1.gridy++;
		myPanel.add(createUtilitiesPanel(myPlotter),con1);	con1.gridy++;
		myPanel.add(createAxisDrivingControls(),con1);		con1.gridy++;
		myPanel.add(createDrawImagePanel(),con1);			con1.gridy++;

		// always have one extra empty at the end to push everything up.
		con1.weighty = 1;
		myPanel.add(new JLabel(), con1);
		
		// lastly, set the button states
		updateButtonAccess();

		myController.addListener(this);
		myPlotter.addListener(this);
		
		dialog.setContentPane(myPanel);
		dialog.pack();
		dialog.setVisible(true);
		
		// wait while user does stuff...
		
		myPlotter.closeConnection();
		myPlotter.removeListener(this);
	}


	protected List<LoadFile> loadFileSavers() {
		return new ArrayList<LoadFile>();
	}


	private JButton createTightJButton(String label) {
		JButton b = new JButton(label);
		//b.setMargin(new Insets(0,0,0,0));
		Dimension d = new Dimension(60,20);
		b.setPreferredSize(d);
		b.setMaximumSize(d);
		b.setMinimumSize(d);
		b.addActionListener(this);
		return b;
	}


	private JButton createNarrowJButton(String label) {
		JButton b = new JButton(label);
		b.setMargin(new Insets(0,0,0,0));
		b.setPreferredSize(new Dimension(40,20));
		b.addActionListener(this);
		return b;
	}


	private JPanel createJogMotorsPanel(final Plotter plotter) {
		CollapsiblePanel jogPanel = new CollapsiblePanel(Translator.get("DrivePlotterGUI.JogMotors"));
		JPanel jogInterior = jogPanel.getContentPane().getInteriorPanel();
		jogInterior.setLayout(new GridBagLayout());
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill=GridBagConstraints.HORIZONTAL;
		gbc.anchor=GridBagConstraints.NORTH;
		gbc.gridx=0;
		gbc.gridy=0;
		gbc.weightx=1;
			
		jogInterior.add(buttonLeftIn = new JButton(Translator.get("DrivePlotterGUI.JogLeftIn")),gbc);
		buttonLeftIn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				plotter.jogLeftMotorIn();
			}
		});

		gbc.gridx=1;
		jogInterior.add(new JLabel(""),gbc);
		
		gbc.gridx=2;
		jogInterior.add(buttonRightIn = new JButton(Translator.get("DrivePlotterGUI.JogRightIn")),gbc);
		buttonRightIn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				plotter.jogRightMotorIn();
			}
		});
		
		gbc.gridy++;
		gbc.gridx=0;
		jogInterior.add(buttonLeftOut = new JButton(Translator.get("DrivePlotterGUI.JogLeftOut")),gbc);
		buttonLeftOut.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				plotter.jogLeftMotorOut();
			}
		});
		
		gbc.gridx=1;
		jogInterior.add(new JLabel(""),gbc);
		
		gbc.gridx=2;
		jogInterior.add(buttonRightOut = new JButton(Translator.get("DrivePlotterGUI.JogRightOut")),gbc);
		buttonRightOut.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				plotter.jogRightMotorOut();
			}
		});
		
		return jogPanel;
	}
	
	protected JPanel createConnectSubPanel() {
		connectionPanel = new SelectPanel();
				
        buttonConnect = new SelectButton(Translator.get("DrivePlotterGUI.ButtonConnect"));
        buttonConnect.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if(isConnected) {
					myController.halt();
					myPlotter.closeConnection();
					buttonConnect.setText(Translator.get("DrivePlotterGUI.ButtonConnect"));
					buttonConnect.setForeground(Color.GREEN);
					isConnected=false;
					updateButtonAccess();
				} else {
					// network connections
					ConnectionManager connectionManager = new ConnectionManager();
					NetworkConnection s = connectionManager.requestNewConnection(parentFrame);
					if(s!=null) {
						Log.message("Connected.");
						buttonConnect.setText(Translator.get("DrivePlotterGUI.ButtonDisconnect"));
						buttonConnect.setForeground(Color.RED);
						myPlotter.openConnection( s );
					}
					isConnected=true;
				}
			}
		});
        buttonConnect.setForeground(Color.GREEN);

        connectionPanel.add(buttonConnect);

	    return connectionPanel;
	}

	private JPanel createUtilitiesPanel(final Plotter plotter) {
		CollapsiblePanel utilitiesPanel = new CollapsiblePanel(Translator.get("DrivePlotterGUI.Utilities"));
		JPanel panelInterior = utilitiesPanel.getContentPane().getInteriorPanel();

		panelInterior.setLayout(new GridBagLayout());
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill=GridBagConstraints.HORIZONTAL;
		gbc.anchor=GridBagConstraints.NORTH;
		gbc.gridx=0;
		gbc.gridy=0;
		gbc.weightx=1;
		gbc.gridwidth=1;
		
		toggleEngageMotor = new JButton(Translator.get("DrivePlotterGUI.EngageMotors"));
		panelInterior.add(toggleEngageMotor,gbc);
		toggleEngageMotor.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				plotter.engageMotors();
			}
		});

		toggleDisengageMotor = new JButton(Translator.get("DrivePlotterGUI.DisengageMotors"));
		panelInterior.add(toggleDisengageMotor,gbc);
		toggleDisengageMotor.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				plotter.disengageMotors();
			}
		});
		
		
		gbc.gridy++;
		panelInterior.add(new JSeparator(),gbc);
		
		gbc.gridy++;
		penUp = new JButton(Translator.get("DrivePlotterGUI.PenUp"));
		panelInterior.add(penUp,gbc);
		penUp.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				plotter.raisePen();
			}
		});
		
		gbc.gridy++;
		penDown = new JButton(Translator.get("DrivePlotterGUI.PenDown"));
		panelInterior.add(penDown,gbc);
		penDown.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				myPlotter.lowerPen();
			}
		});
		
		gbc.gridy++;
		panelInterior.add(new JSeparator(),gbc);

		gbc.gridy++;
		setHome = new JButton(Translator.get("DrivePlotterGUI.SetHome"));
	    panelInterior.add(setHome,gbc);
	    setHome.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				myPlotter.setHome();
				updateButtonAccess();
			}
		});

		gbc.gridy++;
		findHome = new JButton(Translator.get("DrivePlotterGUI.FindHome"));
		panelInterior.add(findHome,gbc);
		findHome.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				myPlotter.findHome();
			}
		});

		gbc.gridy++;
		goHome = new JButton(Translator.get("DrivePlotterGUI.GoHome"));
		panelInterior.add(goHome,gbc);
		goHome.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				myPlotter.goHome();	
			}
		});
		
		return utilitiesPanel;
	}
	
	private JPanel createDrawImagePanel() {
		CollapsiblePanel drawImagePanel = new CollapsiblePanel(Translator.get("DrivePlotterGUI.drawImagePanel"));
		JPanel panelInterior = drawImagePanel.getContentPane().getInteriorPanel();

		panelInterior.setLayout(new GridBagLayout());
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill=GridBagConstraints.HORIZONTAL;
		gbc.anchor=GridBagConstraints.NORTH;
		gbc.gridx=0;
		gbc.gridy=0;
		gbc.weightx=1;
		gbc.gridwidth=1;
		
		gbc.gridy++;
		buttonStart = new JButton(Translator.get("DrivePlotterGUI.Start"));
		panelInterior.add(buttonStart,gbc);
		buttonStart.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				myController.startAt(0);
			}
		});

		gbc.gridy++;
		buttonStartAt = new JButton(Translator.get("DrivePlotterGUI.StartAtLine"));
		panelInterior.add(buttonStartAt,gbc);
		buttonStartAt.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				startAt();
			}
		});

		gbc.gridy++;
		buttonPause = new JButton(Translator.get("DrivePlotterGUI.Pause"));
		panelInterior.add(buttonPause,gbc);
		buttonPause.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// toggle pause
				if (myController.isPaused()) {
					buttonPause.setText(Translator.get("DrivePlotterGUI.Pause"));
					myController.unPause();
					myController.sendFileCommand();
				} else {
					buttonPause.setText(Translator.get("DrivePlotterGUI.Unpause"));
					myController.pause();
				}
			}
		});

		
		gbc.gridy++;
		buttonHalt = new JButton(Translator.get("DrivePlotterGUI.Halt"));
		panelInterior.add(buttonHalt,gbc);
		buttonHalt.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				myController.halt();	
			}
		});

		gbc.gridy++;
		statusBar = new StatusBar();
		panelInterior.add(statusBar, gbc);
		
		return drawImagePanel;
	}
	
	// manual cartesian driving
	private CollapsiblePanel createAxisDrivingControls() {
		CollapsiblePanel drivePanel = new CollapsiblePanel(Translator.get("DrivePlotterGUI.AxisDriveControls"));
		JPanel panelInterior = drivePanel.getContentPane().getInteriorPanel();
		panelInterior.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill=GridBagConstraints.BOTH;
		gbc.anchor=GridBagConstraints.CENTER;
		gbc.gridx=0;
		gbc.gridy=0;

		down100 = createTightJButton("-100");
		down10 = createTightJButton("-10");
		down1 = createTightJButton("-1");

		up1 = createTightJButton("1");
		up10 = createTightJButton("10");
		up100 = createTightJButton("100");

		left100 = createNarrowJButton("-100");
		left10 = createNarrowJButton("-10");
		left1 = createNarrowJButton("-1");
		
		right1 = createNarrowJButton("1");
		right10 = createNarrowJButton("10");
		right100 = createNarrowJButton("100");

		gbc.gridx=3;  gbc.gridy=6;  panelInterior.add(down100,gbc);
		gbc.gridx=3;  gbc.gridy=5;  panelInterior.add(down10,gbc);
		gbc.gridx=3;  gbc.gridy=4;  panelInterior.add(down1,gbc);

		gbc.gridx=0;  gbc.gridy=3;  panelInterior.add(left100,gbc);
		gbc.gridx=1;  gbc.gridy=3;  panelInterior.add(left10,gbc);
		gbc.gridx=2;  gbc.gridy=3;  panelInterior.add(left1,gbc);
		
		gbc.gridx=4;  gbc.gridy=3;  panelInterior.add(right1,gbc);
		gbc.gridx=5;  gbc.gridy=3;  panelInterior.add(right10,gbc);
		gbc.gridx=6;  gbc.gridy=3;  panelInterior.add(right100,gbc);
		
		gbc.gridx=3;  gbc.gridy=2;  panelInterior.add(up1,gbc);
		gbc.gridx=3;  gbc.gridy=1;  panelInterior.add(up10,gbc);
		gbc.gridx=3;  gbc.gridy=0;  panelInterior.add(up100,gbc);

		return drivePanel;
	}
	
	// The user has done something. respond to it.
	@Override
	public void actionPerformed(ActionEvent e) {
		Object subject = e.getSource();
		
		float dx=0;
		float dy=0;
		
		if (subject == down100) dy = -100;
		if (subject == down10) dy = -10;
		if (subject == down1) dy = -1;
		if (subject == up100) dy = 100;
		if (subject == up10) dy = 10;
		if (subject == up1) dy = 1;
		
		if (subject == left100) dx = -100;
		if (subject == left10) dx = -10;
		if (subject == left1) dx = -1;
		if (subject == right100) dx = 100;
		if (subject == right10) dx = 10;
		if (subject == right1) dx = 1;

		if(dx!=0 || dy!=0) {
			myPlotter.movePenRelative(dx,dy);
		}
	}
	
	protected void startAt() {
		PanelStartAt p = new PanelStartAt();
		if(p.run(parentFrame)) {
			// user hit ok
			int lineNumber = p.lineNumber;
			if (lineNumber != -1) {
				if(p.findPreviousPenDown==false) {
					if(p.addPenDownCommand==true) {
						myPlotter.sendLineToRobot(myPlotter.getPenDownString());
					}
					myController.startAt(lineNumber);
				} else {
					int lineBefore = myController.findLastPenUpBefore(lineNumber);
					myController.startAt(lineBefore);
				}
			}
		}
	}
	
	// the moment a robot is confirmed to have connected
	public void onConnect() {
		updateButtonAccess();
		myPlotter.engageMotors();
	}
	
	public void updateButtonAccess() {
		boolean isConfirmed=false;
		boolean isRunning=false;
		boolean didSetHome=false;
				
		if(myPlotter!=null) {
			isConfirmed = myPlotter.isPortConfirmed();
			isRunning = myController.isRunning();
			didSetHome = myPlotter.didSetHome();
		}
		
		buttonRightIn.setEnabled(isConfirmed && !isRunning);
		buttonLeftIn.setEnabled(isConfirmed && !isRunning);
		buttonRightOut.setEnabled(isConfirmed && !isRunning);
		buttonLeftOut.setEnabled(isConfirmed && !isRunning);
		
		if(buttonHalt!=null) buttonHalt.setEnabled(isConfirmed && isRunning);
		if(buttonStart!=null) buttonStart.setEnabled(isConfirmed && didSetHome && !isRunning);
		if(buttonStartAt!=null) buttonStartAt.setEnabled(isConfirmed && didSetHome && !isRunning);
		if(buttonPause!=null) {
			buttonPause.setEnabled(isConfirmed && isRunning);
			if(!isConfirmed) {
				buttonPause.setText(Translator.get("DrivePlotterGUI.Pause"));
			}
		}
		
		toggleEngageMotor.setEnabled(isConfirmed && !isRunning);
		toggleDisengageMotor.setEnabled(isConfirmed && !isRunning);

		down100.setEnabled(isConfirmed && !isRunning);
		down10.setEnabled(isConfirmed && !isRunning);
		down1.setEnabled(isConfirmed && !isRunning);
		up1.setEnabled(isConfirmed && !isRunning);
		up10.setEnabled(isConfirmed && !isRunning);
		up100.setEnabled(isConfirmed && !isRunning);

		left100.setEnabled(isConfirmed && !isRunning);
		left10.setEnabled(isConfirmed && !isRunning);
		left1.setEnabled(isConfirmed && !isRunning);
		right1.setEnabled(isConfirmed && !isRunning);
		right10.setEnabled(isConfirmed && !isRunning);
		right100.setEnabled(isConfirmed && !isRunning);

		setHome .setEnabled( isConfirmed && !isRunning && !myPlotter.canAutoHome() );
		findHome.setEnabled( isConfirmed && !isRunning &&  myPlotter.canAutoHome() );
		goHome.setEnabled(isConfirmed && !isRunning && didSetHome);
		
		penUp.setEnabled(isConfirmed && !isRunning);
		penDown.setEnabled(isConfirmed && !isRunning);
		
		myPanel.validate();
	}
	
	public static void main(String[] argv) {
		if(GraphicsEnvironment.isHeadless()) {
			System.out.println("Test can only be run on a machine with a head (monitor, HID)");
			return;
		}	
		
		Log.start();
		CommandLineOptions.setFromMain(argv);
		Translator.start();
		
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				JFrame mainFrame = new JFrame(Translator.get("DrivePlotterGUI.title"));
				mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

				Plotter p = new Makelangelo5();
				RobotController rc = new RobotController(p);
				DrivePlotterGUI gui = new DrivePlotterGUI(rc);
				gui.run(mainFrame);
			}
		});
	}

	@Override
	public void connectionConfirmed(Plotter r) {		
		onConnect();
		
		if (parentFrame != null) {
			parentFrame.invalidate();
		}
		updateButtonAccess();
	}

	@Override
	public void firmwareVersionBad(Plotter r,long versionExpected, long versionFound) {
		(new DialogBadFirmwareVersion()).display(parentFrame, Long.toString(versionExpected), Long.toString(versionFound));
	}

	@Override
	public void dataAvailable(Plotter r, String data) {
		if (data.endsWith("\n"))
			data = data.substring(0, data.length() - 1);
		Log.message(data); // #ffa500 = orange
	}

	@Override
	public void sendBufferEmpty(Plotter r) {}

	@Override
	public void lineError(Plotter r, int lineNumber) {}

	@Override
	public void disconnected(Plotter r) {
		if (parentFrame != null) {
			parentFrame.invalidate();
		}
		SoundSystem.playDisconnectSound();
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if(evt.getSource() == myPlotter) {
			switch(evt.getPropertyName()) {
			case "engaged":
				if((boolean)evt.getNewValue()) {
					toggleEngageMotor.setText(Translator.get("DrivePlotterGUI.DisengageMotors"));
				} else {
					toggleEngageMotor.setText(Translator.get("DrivePlotterGUI.EngageMotors"));
				}
				break;
			}
		} else if(evt.getSource()==myController) {
			switch(evt.getPropertyName()) {
			case "progress":
				statusBar.setProgress((int)evt.getOldValue(), (int)evt.getNewValue());
				break;
			case "running":
				if((boolean)evt.getNewValue()==true) {
					statusBar.start();
				}
				updateButtonAccess();
				break;
			}
		}
	}
}
