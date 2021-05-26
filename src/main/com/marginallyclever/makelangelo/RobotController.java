package com.marginallyclever.makelangelo;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import com.marginallyclever.core.StringHelper;
import com.marginallyclever.core.Translator;
import com.marginallyclever.core.log.Log;
import com.marginallyclever.core.node.Node;
import com.marginallyclever.core.turtle.Turtle;
import com.marginallyclever.core.turtle.TurtleMove;
import com.marginallyclever.makelangelo.nodes.gcode.SaveGCode;
import com.marginallyclever.makelangelo.plotter.FirmwareSimulation;
import com.marginallyclever.makelangelo.plotter.Plotter;
import com.marginallyclever.makelangelo.plotter.PlotterListener;

/**
 * A {@link RobotController} talks to a {@link Plotter}.  It converts a set of {@link Turtle}s to plotter commands
 * and delivers them one at a time to draw pictures.
 * 
 * @see <a href='https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller'>Model-View-Controller design pattern</a>. 
 * 
 * @author Dan Royer
 * @since 7.2.10
 */
public class RobotController extends Node implements PlotterListener {
	public Plotter myPlotter;

	private ArrayList<Turtle> turtles = new ArrayList<Turtle>();

	// this list of gcode commands is store separate from the Turtle.
	private ArrayList<String> drawingCommands = new ArrayList<String>();
	
	// what line in drawingCommands is going to be sent next?
	protected int drawingProgress;
	
	// Listeners which should be notified of a change to the percentage.
	private ArrayList<PropertyChangeListener> listeners = new ArrayList<PropertyChangeListener>();

	private boolean isRunning;

	private boolean isPaused;
	
	// machine is confirmed ready to go.
	private boolean readyToDraw;

	// pen is lifted when the machine is paused.  Remember state before hand.
	private boolean penIsUpBeforePause;

	
	public RobotController(Plotter plotter) {
		super();
		myPlotter = plotter;
		plotter.addListener(this);
		drawingProgress = 0;
		readyToDraw = false;
		isRunning = false;
		isPaused = false;
		penIsUpBeforePause = false;
	}

	@Override
	public String getName() {
		return Translator.get("RobotController.name");
	}

	public void addListener(PlotterListener listener) {
		listeners.add(listener);
	}

	public void removeListener(PlotterListener listener) {
		listeners.remove(listener);
	}
	
	// notify PropertyChangeListeners
	void notifyListeners(String propertyName,Object oldValue,Object newValue) {
		PropertyChangeEvent e = new PropertyChangeEvent(this,propertyName,oldValue,newValue);
		for(PropertyChangeListener ear : listeners) {
			ear.propertyChange(e);
		}
	}

	/**
	 * Generate a checksum for the given input string by XORing all the bytes in the string.
	 * @param line the string from which the checksum is generated.
	 * @return '*' + the checksum
	 */
	private String generateChecksum(String line) {
		byte checksum = 0;

		for (int i = 0; i < line.length(); ++i) {
			checksum ^= line.charAt(i);
		}

		return "*" + Integer.toString(checksum);
	}

	public void testPenAngle(double testAngle) {
		myPlotter.sendLineToRobot(Plotter.COMMAND_DRAW + " Z" + StringHelper.formatDouble(testAngle));
	}

	/**
	 * Remove comments (anything after ';', including the semi-colon itself), add line number to head, add checksum to tail.
	 * This is part of the Transport Layer in the OSI network model.
	 * @see <a href='https://en.wikipedia.org/wiki/OSI_model#Layer_4:_Transport_Layer'>Transport Layer</a>
	 * @param line command to send
	 * @param lineNumber
	 */
	private void sendLineWithNumberAndChecksum(String line, int lineNumber) {
		if(!readyToDraw || !isRunning)
			return;

		line = "N" + lineNumber + " " + line;
		if(!line.endsWith(";"))
			line += ';';
		String checksum = generateChecksum(line);
		line += checksum;

		// send relevant part of line to the robot
		myPlotter.sendLineToRobot(line);
	}

	/**
	 * Take the next line from the file and send it to the robot, if permitted.
	 * Notify listeners about the progress of the file transmission.
	 */
	public void sendFileCommand() {
		int numCommands = drawingCommands.size();

		if(!readyToDraw || !isRunning || isPaused || numCommands==0 )
			return;

		// are there any more commands?
		if (drawingProgress == numCommands) {
			// no!
			halt();
			notifyListeners("progress",numCommands,numCommands);
			SoundSystem.playDrawingFinishedSound();
		} else {
			// yes!
			String line = drawingCommands.get(drawingProgress);
			sendLineWithNumberAndChecksum(line, drawingProgress);
			drawingProgress++;

			// update the simulated position to match the real robot?
			if (line.contains(Plotter.COMMAND_DRAW) || line.contains(Plotter.COMMAND_TRAVEL)) {
				double px = myPlotter.getPenX();
				double py = myPlotter.getPenY();

				String[] tokens = line.split(" ");
				for (String t : tokens) {
					if (t.startsWith("X")) px = Double.parseDouble(t.substring(1));
					if (t.startsWith("Y")) py = Double.parseDouble(t.substring(1));
				}
				myPlotter.setPenX(px);
				myPlotter.setPenY(py);
			}
			
			notifyListeners("progress",drawingProgress, numCommands);
		}
	}

	public void startAt(int lineNumber) {
		if (drawingCommands.size() == 0)
			return;

		drawingProgress = lineNumber;
		setLineNumber(lineNumber);
		start();
		sendFileCommand();
	}

	public boolean isRunning() {
		return isRunning;
	}

	public boolean isPaused() {
		return isPaused;
	}

	public void pause() {
		if (isPaused)
			return;
	
		isPaused = true;
		// remember for later if the pen is down
		penIsUpBeforePause = myPlotter.isPenUp();
		// raise it if needed.
		myPlotter.raisePen();
	}

	public void unPause() {
		if(!isPaused)
			return;
	
		// if pen was down before pause, lower it
		if(!penIsUpBeforePause) {
			myPlotter.lowerPen();
		}
	
		isPaused = false;
	}

	public void halt() {
		isRunning = false;
		isPaused = false;
		myPlotter.raisePen();
		
		notifyListeners("running", null, false);
	}

	public void start() {
		isRunning = true;

		notifyListeners("running", null, true);
	}

	public double getCurrentFeedRate() {
		return myPlotter.getDrawingFeedRate();
	}

	public void setLineNumber(int newLineNumber) {
		myPlotter.sendLineToRobot("M110 N" + newLineNumber);
	}

	public Plotter getPlotter() {
		return myPlotter;
	}

	public void setTurtles(ArrayList<Turtle> list) {
		turtles.clear();
		turtles.addAll(list);
		
		saveCurrentTurtlesToDrawing();
	}

	// Copy the most recent turtle to the drawing output buffer.
	private void saveCurrentTurtlesToDrawing() {
		int lineCount=0;
		try (final OutputStream fileOutputStream = new FileOutputStream("currentDrawing.ngc")) {
			SaveGCode saveNGC = new SaveGCode();
			saveNGC.save(fileOutputStream, turtles, myPlotter);

			drawingCommands.clear();
			BufferedReader reader = new BufferedReader(new FileReader("currentDrawing.ngc"));
			String line;

			while ((line = reader.readLine()) != null) {
				drawingCommands.add(line.trim());
				++lineCount;
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// old way
		Log.message("Old method "+printTimeEstimate(estimateTime()));

		// new way
		double newEstimate=0;
		FirmwareSimulation m = new FirmwareSimulation();
		for( Turtle t : turtles ) {
			newEstimate += m.getTimeEstimate(t, myPlotter);
		}
		Log.message("New method "+printTimeEstimate(newEstimate));
		
		// show results
		notifyListeners("progress", 0, lineCount);
	}
	
	protected String printTimeEstimate(double seconds) {
		return "Estimate =" + Log.secondsToHumanReadable(seconds);
	}

	protected double estimateTime() {
		double totalTime = 0;
		
		for( Turtle turtle : turtles ) {
			if(turtle.isLocked())
				return totalTime;
		
			turtle.lock();
	
			try {
				boolean isUp = true;
				double ox = myPlotter.getHomeX();
				double oy = myPlotter.getHomeY();
				double oz = myPlotter.getPenUpAngle();
	
				for (TurtleMove m : turtle.history) {
					double nx = ox;
					double ny = oy;
					double nz = oz;
	
					if(m.isUp) {
						if (!isUp) {
							nz = myPlotter.getPenUpAngle();
							isUp = true;
						}
					} else {
						if (isUp) {
							nz = myPlotter.getPenDownAngle();
							isUp = false;
						}
						nx = m.x;
						ny = m.y;
					}
	
					double dx = nx - ox;
					double dy = ny - oy;
					double dz = nz - oz;
					double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (length > 0) {
						double accel = myPlotter.getAcceleration();
						double maxV;
						if (oz != nz) {
							maxV = myPlotter.getZFeedrate();
						} else if (nz == myPlotter.getPenDownAngle()) {
							maxV = myPlotter.getDrawingFeedRate();
						} else {
							maxV = myPlotter.getTravelFeedRate();
						}
						totalTime += estimateSingleBlock(length, 0, 0, maxV, accel);
					}
					ox = nx;
					oy = ny;
					oz = nz;
				}
			} finally {
				turtle.unlock();
			}	
		}
		
		return totalTime;
	}

	/**
	 * calculate seconds to move a given length. Also uses globals feedRate and
	 * acceleration See
	 * http://zonalandeducation.com/mstm/physics/mechanics/kinematics/EquationsForAcceleratedMotion/AlgebraRearrangements/Displacement/DisplacementAccelerationAlgebra.htm
	 * 
	 * @param length    mm distance to travel.
	 * @param startRate mm/s at start of move
	 * @param endRate   mm/s at end of move
	 * @return time to execute move
	 */
	protected double estimateSingleBlock(double length, double startRate, double endRate, double maxV, double accel) {
		double distanceToAccelerate = (maxV * maxV - startRate * startRate) / (2.0 * accel);
		double distanceToDecelerate = (endRate * endRate - maxV * maxV) / (2.0 * -accel);
		double distanceAtTopSpeed = length - distanceToAccelerate - distanceToDecelerate;
		if (distanceAtTopSpeed < 0) {
			// we never reach feedRate.
			double intersection = (2.0 * accel * length - startRate * startRate + endRate * endRate) / (4.0 * accel);
			distanceToAccelerate = intersection;
			distanceToDecelerate = length - intersection;
			distanceAtTopSpeed = 0;
		}
		// time at maxV
		double time = distanceAtTopSpeed / maxV;

		// time accelerating (v=start vel;a=acceleration;d=distance;t=time)
		// 0.5att+vt-d=0
		// att+2vt=2d
		// using quadratic to solve for t,
		// t = (-v +/- sqrt(vv+2ad))/a
		double s;
		s = Math.sqrt(startRate * startRate + 2.0 * accel * distanceToAccelerate);
		double a = (-startRate + s) / accel;
		double b = (-startRate - s) / accel;
		double accelTime = a > b ? a : b;
		if (accelTime < 0) {
			accelTime = 0;
		}

		// time decelerating (v=end vel;a=acceleration;d=distance;t=time)
		s = Math.sqrt(endRate * endRate + 2.0 * accel * distanceToDecelerate);
		double c = (-endRate + s) / accel;
		double d = (-endRate - s) / accel;
		double decelTime = c > d ? c : d;
		if (decelTime < 0) {
			decelTime = 0;
		}

		// sum total
		return time + accelTime + decelTime;
	}

	public int findLastPenUpBefore(int startAtLine) {
		int total = drawingCommands.size();
		if (total == 0)
			return 0;

		String toMatch = myPlotter.getPenUpString();

		int x = startAtLine;
		if (x >= total) {
			x = total - 1;
		}

		toMatch = toMatch.trim();
		while (x > 1) {
			String line = drawingCommands.get(x).trim();
			if (line.equals(toMatch)) {
				return x;
			}
			--x;
		}

		return x;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {}

	@Override
	public void sendBufferEmpty(Plotter r) {
		sendFileCommand();
	}

	@Override
	public void dataAvailable(Plotter r, String data) {}

	@Override
	public void disconnected(Plotter r) {
		halt();
		readyToDraw=false;
	}

	@Override
	public void lineError(Plotter r, int lineNumber) {
		drawingProgress = lineNumber;
	}

	@Override
	public void firmwareVersionBad(Plotter r, long versionExpected, long versionFound) {}

	@Override
	public void connectionConfirmed(Plotter r) {
		readyToDraw=true;
	}
}
