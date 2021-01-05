package com.marginallyclever.convenience;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import com.marginallyclever.convenience.log.Log;


/**
 * A simple turtle implementation to make generating pictures and learning programming easier.
 * @author Admin
 *
 */
public class Turtle implements Cloneable {
	public enum MoveType {
		TRAVEL,  // move without drawing
		DRAW,  // move while drawing
		TOOL_CHANGE;
	}
	
	public static class Movement {
		public MoveType type;
		public double x,y;  // destination
		
		public Movement(double x0,double y0,MoveType type0) {
			x=x0;
			y=y0;
			type=type0;
		}
		
		public Movement(Movement m) {
			this.x=m.x;
			this.y=m.y;
			this.type=m.type;
		}

		public ColorRGB getColor() {
			return new ColorRGB((int)x);
		}
	};
	public ArrayList<Movement> history;

	private ReentrantLock lock;

	// current state
	private double turtleX, turtleY;
	private double turtleDx, turtleDy;
	private double angle;
	private boolean isUp;
	private ColorRGB color;

	
	public Turtle() {
		super();
		lock = new ReentrantLock();
		reset();
	}
	
	public Turtle(Turtle t) {
		this();
		turtleX = t.turtleX;
		turtleY = t.turtleY;
		turtleDx = t.turtleDx;
		turtleDy = t.turtleDy;
		angle = t.angle;
		isUp = t.isUp;
		t.color.set(t.color);

		for( Movement m : t.history ) {
			history.add(new Movement(m));
		}
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		Turtle t = (Turtle)super.clone();
		for( Movement m : history ) {
			t.history.add(new Movement(m));
		}
		return t;
	}
	
	/**
	 * Return this Turtle to mint condition.  Erases history and resets all parameters.  Called by constructor.
	 */
	protected void reset() {
		turtleX = 0;
		turtleY = 0;
		setAngle(0);
		penUp();
		history = new ArrayList<Movement>();
		// default turtle color is black.
		setColor(new ColorRGB(0,0,0));
	}
	
	// multithreading lock safety
	public boolean isLocked() {
		return lock.isLocked();
	}
	
	// multithreading lock safety
	public void lock() {
		lock.lock();
	}
	
	// multithreading lock safety
	public void unlock() {
		if(lock.isLocked()) {  // prevents "illegal state exception - not locked"
			lock.unlock();
		}
	}

	public void setColor(ColorRGB c) {
		if(color!=null) {
			if(color.red==c.red && color.green==c.green && color.blue==c.blue) return;
			color.set(c);
		} else {
			color = new ColorRGB(c);
		}
		history.add( new Movement(c.toInt(),0/*tool diameter?*/,MoveType.TOOL_CHANGE) );
	}
	
	public ColorRGB getColor() {
		return color;
	}
	
	/**
	 * Absolute position change, make sure pen is up before move and put pen down after move.
	 * @param x  
	 * @param y 
	 */
	public void jumpTo(double x,double y) {
		penUp();
		moveTo(x,y);
		penDown();
	}
	
	/**
	 * Absolute position change, do not adjust pen status
	 * @param x  
	 * @param y 
	 */
	public void moveTo(double x,double y) {
		turtleX=x;
		turtleY=y;
		history.add( new Movement(x, y, isUp ? MoveType.TRAVEL : MoveType.DRAW) );
	}
	
	/**
	 * Absolute position
	 * @param arg0 x axis
	 */
	public void setX(double arg0) {
		moveTo(arg0,turtleY);
	}
	
	/**
	 * Absolute position
	 * @param arg0 y axis
	 */
	public void setY(double arg0) {
		moveTo(turtleX,arg0);
	}
	
	public double getX() {
		return turtleX;
	}
	
	public double getY() {
		return turtleY;
	}
	
	public void penUp() {
		isUp=true;
	}
	
	public void penDown() {
		isUp=false;
	}
	
	/**
	 * @return true if pen is up
	 */
	public boolean isUp() {
		return isUp;
	}

	/**
	 * Relative turn
	 * @param degrees
	 */
	public void turn(double degrees) {
		setAngle(angle+degrees);
	}

	// Get absolute angle degrees
	public double getAngle() {
		return angle;
	}
	
	/**
	 * Set absolute angle
	 * @param degrees degrees
	 */
	public void setAngle(double degrees) {
		angle=degrees;
		double radians=Math.toRadians(angle);
		turtleDx = Math.cos(radians);
		turtleDy = Math.sin(radians);
	}

	/**
	 * Relative move forward/back
	 * @param stepSize
	 */
	public void forward(double stepSize) {
		moveTo(
			turtleX + turtleDx * stepSize,
			turtleY + turtleDy * stepSize
		);
	}

	/**
	 * Calculate the limits of drawing lines in this turtle history
	 * @param top maximum limits
	 * @param bottom minimum limits
	 */
	public void getBounds(Point2D top,Point2D bottom) {
		bottom.x=Float.MAX_VALUE;
		bottom.y=Float.MAX_VALUE;
		top.x=-Float.MAX_VALUE;
		top.y=-Float.MAX_VALUE;
		Movement old=null;
		
		for( Movement m : history ) {
			if(m.type == MoveType.DRAW)
			{
				if(top.x<m.x) top.x=m.x;
				if(top.y<m.y) top.y=m.y;
				if(bottom.x>m.x) bottom.x=m.x;
				if(bottom.y>m.y) bottom.y=m.y;
				if(old != null)
				{
					if(top.x<old.x) top.x=old.x;
					if(top.y<old.y) top.y=old.y;
					if(bottom.x>old.x) bottom.x=old.x;
					if(bottom.y>old.y) bottom.y=old.y;
				}
			}
			old=m;
		}
	}

	/**
	 * Scale all draw and move segments by parameters
	 * @param sx
	 * @param sy
	 */
	public void scale(double sx, double sy) {
		for( Movement m : history ) {
			switch(m.type) {
			case DRAW:
			case TRAVEL:
				m.x*=sx;
				m.y*=sy;
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Translate all draw and move segments by parameters
	 * @param dx relative move x
	 * @param dy relative move y
	 */
	public void translate(double dx, double dy) {
		for( Movement m : history ) {
			switch(m.type) {
			case DRAW:
			case TRAVEL:
				m.x+=dx;
				m.y+=dy;
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Log smallest bounding rectangle for Turtle path.
	 */
	public void showExtent() {
		int i;
		double xmin=0,xmax=0,ymin=0,ymax=0;
		int first=1;
		for(i=0;i<history.size();i++)
		{
			Movement mov=history.get(i);
			if (mov.type == MoveType.DRAW)
			{
				if(first == 1 || mov.x < xmin) xmin=mov.x;
				if(first == 1 || mov.y < ymin) ymin=mov.y;
				if(first == 1 || mov.x > xmax) xmax=mov.x;
				if(first == 1 || mov.y > ymax) ymax=mov.y;
				first=0;
			}
		}
		Log.message("extent is ("+xmin+"/"+ymin+" "+xmax+"/"+ymax+" ");
	}
}
