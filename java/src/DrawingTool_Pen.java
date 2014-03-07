import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;




public class DrawingTool_Pen extends DrawingTool {
	
	DrawingTool_Pen() {
		diameter=1.5f;
		z_rate=80;
		z_on=90;
		z_off=50;
		tool_number=0;
		feed_rate=4000;
		name="Pen";
	}
	
	public void Adjust() {
		final JDialog driver = new JDialog(Makelangelo.getSingleton().getParentFrame(),"Adjust Pen",true);
		driver.setLayout(new GridBagLayout());

		final JTextField penDiameter   = new JTextField(Float.toString(diameter),5);
		final JTextField penFeedRate   = new JTextField(Float.toString(feed_rate),5);
		
		final JTextField penUp   = new JTextField(Float.toString(z_on),5);
		final JTextField penDown = new JTextField(Float.toString(z_off),5);
		final JButton buttonTestUp = new JButton("Test");
		final JButton buttonTestDown = new JButton("Test");
		final JButton buttonSave = new JButton("Save");
		final JButton buttonCancel = new JButton("Cancel");
	
		GridBagConstraints c = new GridBagConstraints();
		GridBagConstraints d = new GridBagConstraints();

		c.anchor=GridBagConstraints.EAST;
		c.fill=GridBagConstraints.HORIZONTAL;
		d.anchor=GridBagConstraints.WEST;
		d.fill=GridBagConstraints.HORIZONTAL;
		d.weightx=50;
		int y=0;

		c.gridx=0;	c.gridy=y;	driver.add(new JLabel("Diameter"),c);
		d.gridx=1;	d.gridy=y;	driver.add(penDiameter,d);
		++y;

		c.gridx=0;	c.gridy=y;	driver.add(new JLabel("Max feed rate"),c);
		d.gridx=1;	d.gridy=y;	driver.add(penFeedRate,d);
		++y;

		c.gridx=0;	c.gridy=y;	driver.add(new JLabel("Up"),c);
		d.gridx=1;	d.gridy=y;	driver.add(penUp,d);
		d.gridx=2;	d.gridy=y;	driver.add(buttonTestUp,d);
		++y;

		c.gridx=0;	c.gridy=y;	driver.add(new JLabel("Down"),c);
		d.gridx=1;	d.gridy=y;	driver.add(penDown,d);
		d.gridx=2;	d.gridy=y;	driver.add(buttonTestDown,d);
		++y;
	
		c.gridx=1;	c.gridy=y;	driver.add(buttonSave,c);
		c.gridx=2;	c.gridy=y;	driver.add(buttonCancel,c);
		++y;
	
		c.gridwidth=2;
		c.insets=new Insets(0,5,5,5);
		c.anchor=GridBagConstraints.WEST;
		
		c.gridheight=4;
		c.gridx=0;  c.gridy=y;
		driver.add(new JTextArea("Adjust the values sent to the servo to\n" +
								 "raise and lower the pen."),c);
		
		
		ActionListener driveButtons = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Object subject = e.getSource();
				
				if(subject == buttonTestUp) {
					Makelangelo.getSingleton().SendLineToRobot("G00 Z"+Float.valueOf(penUp.getText()));
				}
				if(subject == buttonTestDown) {
					Makelangelo.getSingleton().SendLineToRobot("G00 Z"+Float.valueOf(penDown.getText()));
				}
				if(subject == buttonSave) {
					diameter = Float.valueOf(penDiameter.getText());
					feed_rate = Float.valueOf(penFeedRate.getText());
					z_on = Float.valueOf(penUp.getText());
					z_off = Float.valueOf(penDown.getText());
					MachineConfiguration.getSingleton().SaveConfig();
					driver.dispose();
				}
				if(subject == buttonCancel) {
					driver.dispose();
				}
			}
		};
		
		buttonTestUp.addActionListener(driveButtons);
		buttonTestDown.addActionListener(driveButtons);
		
		buttonSave.addActionListener(driveButtons);
		buttonCancel.addActionListener(driveButtons);
	
		Makelangelo.getSingleton().SendLineToRobot("M114");
		driver.pack();
		driver.setVisible(true);
	}

}
