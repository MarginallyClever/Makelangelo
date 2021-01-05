package com.marginallyclever.artPipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.JOptionPane;

import com.marginallyclever.convenience.Clipper2D;
import com.marginallyclever.convenience.ColorRGB;
import com.marginallyclever.convenience.MathHelper;
import com.marginallyclever.convenience.Point2D;
import com.marginallyclever.convenience.Turtle;
import com.marginallyclever.convenience.Turtle.MoveType;
import com.marginallyclever.convenience.Turtle.Movement;
import com.marginallyclever.convenience.log.Log;
import com.marginallyclever.makelangeloRobot.settings.MakelangeloRobotSettings;

/**
 * See https://www.marginallyclever.com/2019/12/lets-talk-about-where-makelangelo-software-is-going-in-2020/
 * @author Dan Royer
 * 
 */
public class ArtPipeline {
	/** 
	 * line segments with color 
	 * @author Dan Royer
	 *
	 */
	public class Line2D {
		public Point2D a,b;
		public ColorRGB c;
		public boolean flag;

		public Line2D(Point2D a, Point2D b, ColorRGB c) {
			super();
			this.a = a;
			this.b = b;
			this.c = c;
		}
		
		public void flip() {
			Point2D temp=b;
			b=a;
			a=temp;
		}
		public String toString() {
			return "("+a.x+","+a.y+")-("+b.x+","+b.y+")";
		}
		public double physicalLengthSquared() {
			double dx=a.x-b.x;
			double dy=a.y-b.y;
			return dx*dx + dy*dy;
		}
		
		public double ptSegDistSq(Point2D point) {
			// The distance measured is the distance between the specified point,
			// and the closest point between the start and end points of line a. 
			return java.awt.geom.Line2D.ptSegDistSq(a.x, a.y, b.x, b.y, point.x, point.y);
		}
		
		public double ptLineDistSq(Point2D point) {
			// The distance measured is the distance between the specified point,
			// and the closest point on the infinite extension of line a.
			return java.awt.geom.Line2D.ptLineDistSq(a.x, a.y, b.x, b.y, point.x, point.y);
		}
	}
	
	/**
	 * A sequence of 2D lines forming a polyline.
	 * the end of line [n] should match the start of line [n+1].
	 * if end of [last] matches start of [0] then this is a closed loop.
	 * @author Dan Royer
	 *
	 */
	public class Sequence2D {
		public ArrayList<Line2D> lines;
		boolean isClosed;
		
		public Sequence2D() {
			lines = new ArrayList<Line2D>();
			isClosed=false;
		}
		
		public void flip() {
			try {
				Collections.reverse(lines);
			} catch(Exception e) {
				e.printStackTrace();
			}
			for( Line2D line : lines ) {
				line.flip();
			}
		}
	}
	
	protected ArtPipelinePanel myPanel;
	
	protected ArrayList<ArtPipelineListener> listeners = new ArrayList<ArtPipelineListener>();
	
	public void addListener(ArtPipelineListener arg0) {
		listeners.add(arg0);
	}
	
	public void removeListener(ArtPipelineListener arg0) {
		listeners.remove(arg0);
	}
	
	public void notifyListenersTurtleFinished(Turtle t) {
		for(ArtPipelineListener p : listeners) {
			p.turtleFinished(t);
		}
	}
	
	private void extendLine(Line2D targetLine, Point2D extPoint) {
		// extPoint is supposed to be a point which lies (almost) on the infinite extension of targetLine
		double newLengthA = distanceBetweenPointsSquared(targetLine.a, extPoint);
		double newLengthB = distanceBetweenPointsSquared(targetLine.b, extPoint);
		double currentLength = targetLine.physicalLengthSquared();
		
		// Maximize length of target line by replacing the start or end point with the extPoint		
		if(newLengthA > currentLength && newLengthA > newLengthB) {
			// Draw line from targetLine.a to extPoint
			targetLine.b = extPoint;
		} else if(newLengthB > currentLength) {
			// Draw line from extPoint to targetLine.b 
			targetLine.a = extPoint;
		}
	}
	
	/**
	 * Offers to look for a better route through the turtle history that means fewer travel moves.
	 * @param turtle
	 * @param settings
	 */
	public void reorder(Turtle turtle, MakelangeloRobotSettings settings) {
		if(turtle.history.size()==0) return;
		
		Log.message("reorder() begin");
		// history is made of changes, travels, and draws
		// look at the section between two changes.
		//   look at all pen down moves in the section.
		//     if two pen down moves share a start/end, then they are connected in sequence.
		
		// build a list of all the pen-down lines while remembering their color.
		ArrayList<Line2D> originalLines = new ArrayList<Line2D>();
		Movement previousMovement=null;
		ColorRGB color = new ColorRGB(0,0,0);

		Log.message("  Found "+turtle.history.size()+" instructions.");
		
		for( Movement m : turtle.history ) {
			switch(m.type) {
			case DRAW:
				if(previousMovement!=null) {
					Line2D line = new Line2D(
							new Point2D(previousMovement.x,previousMovement.y),
							new Point2D(m.x,m.y),
							color);
					if(line.physicalLengthSquared()>0) {
						originalLines.add(line);
					}
				}
				previousMovement = m;
				break;
			case TRAVEL:
				previousMovement = m;
				break;
			case TOOL_CHANGE:
				color = m.getColor();
				break;
			}
		}
		
		int nrOfOriginalLines = originalLines.size();

		Log.message("  Converted to "+nrOfOriginalLines+" lines.");

		final double EPSILON = 0.01;
		final double EPSILON2 = EPSILON*EPSILON;
		double EPSILON_CONNECTED=0.5;  // TODO: make this user-tweakable. Is it in millimeters? 

		ArrayList<Line2D> uniqueLines = new ArrayList<Line2D>();
		
		// TODO: dedupe should be optional so user can reorder without dedupe, or dedupe without reorder
		// remove duplicate lines.
		// TODO: how to handle duplicate lines with different colors? 

		for(Line2D candidateLine : originalLines) {
			int b = 0;
			int end = uniqueLines.size();
			boolean isDuplicate = false;
			Line2D lineToReplace = null;
			
			// Compare this line to all the lines previously marked as non-duplicate
			while (b < end) {
				Line2D uniqueLine = uniqueLines.get(b);	
				++b;
				
				// Check first if lines are (almost) parallel
				/*
				double dx1 = uniqueLine.b.x - uniqueLine.a.x;
				double dy1 = uniqueLine.b.y - uniqueLine.a.y;
				double dx2 = candidateLine.b.x - candidateLine.a.x;
				double dy2 = candidateLine.b.y - candidateLine.a.y;
				
				double cosAngle = Math.abs((dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2)));
				
				// Check if lines have the same angle with a tolerance of 0.25 degrees
				if(cosAngle < 0.99999048) {
					// Not parallel, check remaining lines for duplicates
					continue;
				}
				*/
				
				// Check if lines are (almost) colinear
				if(uniqueLine.ptLineDistSq(candidateLine.a) < EPSILON2 && uniqueLine.ptLineDistSq(candidateLine.b) < EPSILON2){
					// Both lines are (almost) colinear,
					// if they touch they can be merged
					boolean candidateStartsCloseToUnique = uniqueLine.ptSegDistSq(candidateLine.a) < EPSILON2;
					boolean candidateEndsCloseToUnique = uniqueLine.ptSegDistSq(candidateLine.b) < EPSILON2;
					boolean uniqueStartsCloseToCandidate = candidateLine.ptSegDistSq(uniqueLine.a) < EPSILON2;
					boolean uniqueEndsCloseToCandidate = candidateLine.ptSegDistSq(uniqueLine.b) < EPSILON2;
					
					if(candidateStartsCloseToUnique) {
						isDuplicate = true;
						
						if(candidateEndsCloseToUnique) {
							// Candidate doesn't add anything which isn't already covered by the unique line.
							// No further action needed.
							
							// TODO: extend the line, to ensure no gaps will arise due to the configured tolerance?
							// extendLine(uniqueLine, candidateLine.a);
							// extendLine(uniqueLine, candidateLine.b);
						} else {
							// Partial overlap, extend uniqueLine
							extendLine(uniqueLine, candidateLine.b);
						}
					} else if(candidateEndsCloseToUnique) {
						isDuplicate = true;
						// Partial overlap, extend uniqueLine
						extendLine(uniqueLine, candidateLine.a);						
					} else if(uniqueStartsCloseToCandidate) {
						if(uniqueEndsCloseToCandidate) {
							// The candidateLine covers more than the unique line already added,
							// replace uniqueLine with candidateLine.
							lineToReplace = uniqueLine;
							// No further action needed.
						} else {
							isDuplicate = true;
							// Partial overlap, extend uniqueLine
							extendLine(uniqueLine, candidateLine.a);
						}
					} else {
						// No match, check remaining lines for duplicates
						continue;
					}
					
					// Match found, no need to continue search
					break;
				}
			}
			
			if(!isDuplicate) {
				if(lineToReplace != null) {
					uniqueLines.remove(lineToReplace);
				}
				// candidateLine does not match any line in the list.
				uniqueLines.add(candidateLine);					
			}
		}
		
		int duplicates = nrOfOriginalLines - uniqueLines.size();
		Log.message("  - "+duplicates+" duplicates = "+uniqueLines.size()+" lines.");
		
		Turtle t = new Turtle();
		// I assume the turtle history starts at the home position.
		t.setX(turtle.history.get(0).x);
		t.setY(turtle.history.get(0).y);
		
		ArrayList<Line2D> orderedLines = new ArrayList<Line2D>();
		
		Point2D lastPosition = new Point2D(t.getX(), t.getY());
		
		// Greedy reorder lines
		while(!uniqueLines.isEmpty()) {
			// Continue for as long as there are lines to reorder
			double bestD = Double.MAX_VALUE;
			int bestCandidateIndex = 0;
			int candidateIndex = 0;
			int end = uniqueLines.size();
			boolean shouldFlip = false;
			
			while (candidateIndex < end) {
				// Check all remaining lines, and pick the one with the start or end point
				// closest to lastPosition (the end point of the previous line).
				Line2D candidateLine = uniqueLines.get(candidateIndex);
				double distanceToStartPoint = distanceBetweenPointsSquared(lastPosition, candidateLine.a);
				double distanceToEndPoint = distanceBetweenPointsSquared(lastPosition, candidateLine.b);
				
				boolean shouldFlipCandidate = false;
				double smallestCandidateDistance = distanceToStartPoint;
				
				if(distanceToEndPoint < distanceToStartPoint) {
					// The end point is closer than the start point.
					// Line should be flipped if it's the best candidate in this iteration.
					shouldFlipCandidate = true;
					smallestCandidateDistance = distanceToEndPoint;
				}
				
				if(smallestCandidateDistance < bestD) {
					// This line outperforms the previous candidate,
					// use values from this line instead
					shouldFlip = shouldFlipCandidate;
					bestD = smallestCandidateDistance;
					bestCandidateIndex = candidateIndex;
				}
				
				++candidateIndex;
			}
			
			// Found line closest to lastPosition,
			// remove it from the pool
			Line2D bestCandidate = uniqueLines.remove(bestCandidateIndex);
			if(shouldFlip) {
				// Distance is shortest when this line is flipped
				bestCandidate.flip();
			}
			
			// And add it to the list of reordered lines.
			orderedLines.add(bestCandidate);
			// Start next iteration where current line ends.
			lastPosition = bestCandidate.b;
		}
		
		// Rebuild the turtle history.
		for( Line2D line : orderedLines ) {
			// change color if needed
			if(line.c!=t.getColor()) {
				t.setColor(line.c);
			}
			
			Point2D currentPosition = new Point2D(t.getX(), t.getY());
			if(distanceBetweenPointsSquared(currentPosition, line.a) > EPSILON_CONNECTED) {
				// The previous line ends too far from the start point of this line,
				// need to make a travel with the pen up to the start point of this line.
				t.jumpTo(line.a.x,line.a.y);
			} else {
				// The previous line ends close to the start point of this line,
				// so there's no need to go to the start point of this line since the pen is practically there.
				// The start point of this line will be skipped.
			}
			// Make a pen down move to the end of this line
			t.moveTo(line.b.x,line.b.y);
		}

		Log.message("  History now "+t.history.size()+" instructions.");
		turtle.history = t.history;
		// TODO show updated drawing time.
		Log.message("reorder() end");
	}

	public double distanceBetweenPointsSquared(Turtle.Movement a,Turtle.Movement b) {
		double dx = a.x-b.x;
		double dy = a.y-b.y;
		return MathHelper.lengthSquared(dx, dy); 
	}

	public double distanceBetweenPointsSquared(Point2D a,Point2D b) {
		double dx = a.x-b.x;
		double dy = a.y-b.y;
		return dx*dx + dy*dy; 
	}
	
	public boolean thesePointsAreTheSame(Point2D a,Point2D b,double epsilon) {
		if(a==b) return true;
		
		// close enough ?
		double dx = a.x-b.x;
		double dy = a.y-b.y;
		//if(dx*dx>epsilon*epsilon) return false;
		//if(dy*dy>epsilon*epsilon) return false;
		return MathHelper.lengthSquared(dx, dy)<=epsilon*epsilon; 
	}
	
	/**
	 * Offers to optimize your gcode by chopping out very short line segments.
	 * It travels the entire path and drops any pen-down segment shorter than 
	 * minimumStepSize.
	 * @param turtle
	 * @param settings
	 */
	public void simplify(Turtle turtle, MakelangeloRobotSettings settings) {
		Log.message("simplify() begin");
		ArrayList<Movement> toKeep = new ArrayList<Movement>();

		double minimumStepSize=1;

		boolean isUp=true;
		double ox=settings.getHomeX();
		double oy=settings.getHomeY();
		double sum=0;
		double dx,dy;
		Movement previous=null;
		
		for( Movement m : turtle.history ) {
			switch(m.type) {
			case DRAW:
				dx=m.x-ox;
				dy=m.y-oy;
				sum+=Math.sqrt(dx*dx+dy*dy);
				if(isUp || sum>minimumStepSize) {
					toKeep.add(m);
					sum=0;
				}isUp=false;
				ox=m.x;
				oy=m.y;
				previous=m;
				break;
			case TRAVEL:
				if(!isUp && sum>0 ) {
					if(previous!=null && previous.type==Turtle.MoveType.DRAW) {
						toKeep.add(previous);
					}
				}
				isUp=true;
				toKeep.add(m);
				ox=m.x;
				oy=m.y;
				sum=0;
				previous=m;
				break;
			default:
				toKeep.add(m);
				previous=m;
				break;
			}
		}
		int os = turtle.history.size();
		int ns = toKeep.size();
		turtle.history = toKeep;
		Log.message("simplify() end (was "+os+" is now "+ns+")");
	}

	
	public class PointBranch {
		PointBranch(double x, double y) {
			this.x = x;
			this.y = y;
			begs = new ArrayList<LineSegment>();
			ends = new ArrayList<LineSegment>();
			ur = ul = br = bl = null;
		}

		double x, y;
		ArrayList<LineSegment> begs;
		ArrayList<LineSegment> ends;
		PointBranch ur, ul, br, bl;
	}

	public class LineSegment {
		PointBranch begind, endind;
		int tool;
	}

	HashMap<Integer, PointBranch> pb_root;

	PointBranch findPoint(int color, double x, double y) {
		PointBranch ptr;
		ptr = pb_root.get(Integer.valueOf(color));
		if (ptr == null) {
			ptr = new PointBranch(x, y);
			pb_root.put(Integer.valueOf(color), ptr);
			return ptr;
		}
		while (ptr != null) {
			if(Math.abs(ptr.x-x) < 0.1 && Math.abs(ptr.y-y) < 0.1)
				return ptr;
			if (y >= ptr.y) {
				if (x >= ptr.x) {
					if (ptr.ur == null)
						return ptr.ur = new PointBranch(x, y);
					ptr = ptr.ur;
				} else {
					if (ptr.ul == null)
						return ptr.ul = new PointBranch(x, y);
					ptr = ptr.ul;
				}
			} else {
				if (x >= ptr.x) {
					if (ptr.br == null)
						return ptr.br = new PointBranch(x, y);
					ptr = ptr.br;
				} else {
					if (ptr.bl == null)
						return ptr.bl = new PointBranch(x, y);
					ptr = ptr.bl;
				}
			}
		}

		return null;
	}

	public void simplify1(Turtle turtle, MakelangeloRobotSettings settings) {
		int os, ns;
		int i, j;
		double x,y;
		PointBranch ind = null, oldind = null,tmp;
		int tool = 0;
		Log.message("checkSimplify() begin");
		os = turtle.history.size();
		pb_root = new HashMap<Integer, PointBranch>();

		ArrayList<LineSegment> segments = new ArrayList<LineSegment>();
		// convert turtle into simple segments
		for (i = 0; i < turtle.history.size(); i++) {
			Movement pt = turtle.history.get(i);
			// make equal points sit in equal bins
			x=Math.floor(pt.x/0.001)*0.001;
			y=Math.floor(pt.y/0.001)*0.001;
			switch (pt.type) {
			case DRAW:
				ind = findPoint(tool, x, y);
				LineSegment seg = new LineSegment();
				seg.begind = oldind;
				seg.endind = ind;
				seg.tool = tool;
				oldind.begs.add(seg);
				ind.ends.add(seg);
				segments.add(seg);
				break;
			case TOOL_CHANGE:
				tool = (int) (pt.x);
				break;
			case TRAVEL:
				ind = findPoint(tool, x, y);
				break;

			}
			oldind = ind;
		}
		// delete duplicate segments
		for (i = 0; i < segments.size(); i++) {
			LineSegment seg = segments.get(i);
			if (seg.tool == -1)
				continue; // aleady invalid
			PointBranch begin = seg.begind;

			// parallel
			Iterator<LineSegment> it;
			LineSegment seg1;
			it=begin.begs.iterator();
			while(it.hasNext())
			{
				seg1=it.next();
				if (seg1 == seg || seg1.tool == -1)
					continue;
				if(seg1.endind == seg.endind)
					seg1.tool=-1;
			}

			// antiparallel
			it=begin.ends.iterator();
			while(it.hasNext())
			{
				seg1=it.next();
				if (seg1.tool == -1)
					continue;
				if(seg1.begind == seg.endind)
					seg1.tool=-1;
			}
		}

		// chain the segments and convert the segments to turtle again
		turtle.history.clear();
		turtle.penUp();
		// process each color once
		Point2D lastpoint = settings.getHome();
		
		Iterator<Integer> it = pb_root.keySet().iterator();
		
		while (it.hasNext()) {
			tool = it.next().intValue();
			turtle.history.add(new Movement(tool, 0/* tool diameter? */, MoveType.TOOL_CHANGE));

			while (true ) {
				
				LineSegment nearest=null;
				double nearest_dist2=0.0;
				boolean reversed=false;
				boolean colreversed=false;
				for (i = 0; i < segments.size(); i++) {
					LineSegment seg = segments.get(i);
					if (seg.tool == -1 ||  seg.tool != tool)
						continue;
					double act_dist2;
					act_dist2=new Point2D(seg.begind.x-lastpoint.x,seg.begind.y-lastpoint.y).lengthSquared();
					if(nearest == null || act_dist2 < nearest_dist2)
					{
							nearest_dist2=act_dist2;
							nearest=seg;
							reversed=false;
					}
					act_dist2=new Point2D(seg.endind.x-lastpoint.x,seg.endind.y-lastpoint.y).lengthSquared();
					if(nearest == null || act_dist2 < nearest_dist2)
					{
							nearest_dist2=act_dist2;
							nearest=seg;
							reversed=true;
					}
				}
				if(nearest == null) // no more segments in this color
					break;

				LineSegment seg=nearest;
				LineSegment col=null;
				PointBranch pos = null;
				PointBranch oldpos = null;
				if(reversed) pos = seg.endind;
				else pos = seg.begind;
				
				turtle.history.add(new Movement(pos.x, pos.y, MoveType.TRAVEL));
				turtle.penDown();
//				Log.message("down on "+pos.x+"/"+pos.y);

				oldpos=pos;
				do {
					
					if(reversed) pos = seg.begind;
					else pos = seg.endind;
					seg.tool = -1;
					turtle.history.add(new Movement(pos.x, pos.y, MoveType.DRAW));
					
					double lx=0,ly=0,px,py,dist;
					seg = null;
					col = null;
					lx=pos.x-oldpos.x;
					ly=pos.y-oldpos.y;
					Iterator<LineSegment> it1;
					LineSegment seg1;


					it1=pos.begs.iterator();
					while(it1.hasNext()) {
						seg1=it1.next();
						if (seg1.tool == -1) continue;
						seg = seg1;
						reversed=false;
						tmp=seg.endind;
						
						px=tmp.x - oldpos.x;
						py=tmp.y - oldpos.y;
						dist=(lx*py-ly*px)/Math.sqrt(lx*lx+ly*ly);
						if(Math.abs(dist) < 0.001) // colinear
						{
							col=seg;
							colreversed=false;
						}
					}

					it1=pos.ends.iterator();
					while(it1.hasNext())	{
						seg1=it1.next();
						if (seg1.tool == -1) continue;
						seg = seg1;
						reversed=true;
						tmp = seg.begind;
						
						px=tmp.x - oldpos.x;
						py=tmp.y - oldpos.y;
						dist=(lx*py-ly*px)/Math.sqrt(lx*lx+ly*ly);
						if(Math.abs(dist) < 0.001) // colinear
						{
							col=seg;
							colreversed=true;
						}
					}
					if(col != null)
					{
						seg=col;
						reversed=colreversed;
						turtle.history.remove(turtle.history.size()-1);
					}
					oldpos=pos;
				} while (seg != null);
				
				turtle.penUp();
//				Log.message("up");
				
				lastpoint=new Point2D(pos.x,pos.y);
			}
		}

		ns = turtle.history.size();
		Log.message("checkSimplify() end (was " + os + " is now " + ns + ")");
	}

	
	

	/**
	 * 
	 * @param turtle
	 * @param settings
	 */
	protected void flipV(Turtle turtle, MakelangeloRobotSettings settings) {	
		turtle.scale(1,-1);
	}

	/**
	 * 
	 * @param turtle
	 * @param settings
	 */
	protected void flipH(Turtle turtle, MakelangeloRobotSettings settings) {	
		turtle.scale(-1,1);
	}
	
	/**
	 * Offers to resize your loaded image to fill the margins completely.
	 * @param turtle
	 * @param settings
	 */
	protected void fitToPaper(Turtle turtle, MakelangeloRobotSettings settings,boolean keepAspect) {
		Point2D top = new Point2D();
		Point2D bottom = new Point2D();
		turtle.getBounds(top, bottom); // image bounds
		
		// find the scale
		double iw = top.x-bottom.x; // image width
		double ih = top.y-bottom.y; // image height
		double pw = settings.getPaperRight()-settings.getPaperLeft();
		double ph = settings.getPaperTop()-settings.getPaperBottom();
		double px = (settings.getPaperRight()+settings.getPaperLeft())*0.5;
		double py = (settings.getPaperTop()+settings.getPaperBottom())*0.5;
		double ratioW=1,ratioH=1;
		ratioH = ph/ih;
		ratioW = pw/iw;
		ratioH  *= (1-settings.getPaperMargin()*0.01);
		ratioW  *= (1-settings.getPaperMargin()*0.01);
		// use > to fill the page.
		
		// and the translation
		double ix = (top.x+bottom.x)*0.5;
		double iy = (top.y+bottom.y)*0.5;
		
		// and apply
		turtle.translate(-ix,-iy);
		if(keepAspect == true)
		{
			double ratio=Math.min(ratioW, ratioH);
			turtle.scale(ratio,ratio);
		}
		else
		{
			turtle.scale(ratioW,ratioH);
		}
		turtle.translate(px,py);
	}
	private void rotatePicture(Turtle turtle, MakelangeloRobotSettings settings) {
		int i;
		double x,y,xn,yn;
		double ang=settings.getRotation();
		double refang=settings.getRotationRef();
		double c=Math.cos((ang-refang)*Math.PI/180.0);
		double s=Math.sin((ang-refang)*Math.PI/180.0);

		for(i=0;i<turtle.history.size();i++)
		{
			x=turtle.history.get(i).x;
			y=turtle.history.get(i).y;
			xn=x*c-y*s;
		    yn=x*s+y*c;
			turtle.history.get(i).x=xn;
			turtle.history.get(i).y=yn;
		}
		settings.setRotationRef(ang);
	}
	/**
	 * 
	 * @param turtle
	 * @param settings
	 */
	protected void cropToPageMargin(Turtle turtle, MakelangeloRobotSettings settings) {
		if(turtle==null) return;
		
		Log.message("cropTurtleToPageMargin() start");

		ArrayList<Movement> oldHistory = turtle.history;
		turtle.history = new ArrayList<Movement>();
		
		// limits we will need for rectangle
		Point2D rMax = new Point2D(settings.getMarginRight(),settings.getMarginTop());
		Point2D rMin = new Point2D(settings.getMarginLeft(),settings.getMarginBottom());
		// working space for clipping
		Point2D P0 = new Point2D(); 
		Point2D P1 = new Point2D(); 
		
		Movement prev=null;
		
		for( Movement m : oldHistory ) {
			switch(m.type) {
			case DRAW:
			case TRAVEL:
				if(prev!=null) {
					P0.set(prev.x, prev.y);
					P1.set(m.x, m.y);
					boolean result = Clipper2D.clipLineToRectangle(P0,P1,rMax,rMin);
					// !result means full crop, do nothing.
					if(result) {
						// partial crop.  Which end(s)?
						boolean startCropped=MathHelper.lengthSquared(P0.x-prev.x, P0.y-prev.y)>1e-8;
						boolean   endCropped=MathHelper.lengthSquared(P1.x-   m.x, P1.y-   m.y)>1e-8;
						
						if(startCropped && endCropped) {
							// crosses rectangle, both ends out.
							turtle.history.add(new Movement(P0.x,P0.y,MoveType.TRAVEL));
							turtle.history.add(m);
							Movement m2=new Movement(P1.x,P1.y,m.type);
							turtle.history.add(m2);
						} else if(!startCropped && !endCropped) {
							turtle.history.add(m);
						} else if(endCropped) {
							// end cropped, leaving the rectangle
							Movement m2=new Movement(P1.x,P1.y,m.type);
							turtle.history.add(m2);
						} else {
							// start cropped, coming back into rectangle
							turtle.history.add(new Movement(P0.x,P0.y,MoveType.TRAVEL));
							turtle.history.add(m);
						}
					}
				}
				prev=m;
				
				break;
			default:
				turtle.history.add(m);
				break;
			}
		}
		
		// There may be some dumb travel moves left. (several travels in a row.)
		
		int oldSize= oldHistory.size();
		int newSize= turtle.history.size();
		Log.message("cropTurtleToPageMargin() end (was "+oldSize+" now "+newSize+")");
	}

	/**
	 * 
	 * @param turtle
	 * @param settings
	 */
	MakelangeloRobotSettings last_settings = null;
	Turtle last_turtle = null;

	public void processTurtle(Turtle turtle, MakelangeloRobotSettings settings) {
		if(turtle == null) turtle=last_turtle;
		if(settings == null) settings=last_settings;
		if(turtle == null) return;
		if(turtle.history.isEmpty()) return;
		last_settings=settings;
		last_turtle=turtle;
		
		while(turtle.isLocked()) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				//e.printStackTrace();
				Log.message("processTurtle wait interrupted.");
				return;
			}
		}
		turtle.lock();
		try {
			double ang=settings.getRotation();
			if(ang != 0.0)
			{
				rotatePicture(turtle,settings);
			}
			if(shouldResizeFill()) fitToPaper(turtle,settings,false);
			if(shouldResizeFit()) fitToPaper(turtle,settings,true);
			if(shouldFlipV()) flipV(turtle,settings);
			if(shouldFlipH()) flipH(turtle,settings);
			if(shouldReorder()) reorder(turtle,settings);
			if(shouldSimplify()) simplify(turtle,settings);
			if(shouldCrop()) cropToPageMargin(turtle,settings);
			removeRedundantToolChanges(turtle);
		}
		finally {
			turtle.unlock();
			notifyListenersTurtleFinished(turtle);
		}
	}

	protected void removeRedundantToolChanges(Turtle t) {
		ArrayList<Turtle.Movement> toKeep = new ArrayList<Turtle.Movement>();
		int size=t.history.size();
		for(int i=0;i<size;++i) {
			Turtle.Movement mi = t.history.get(i);
			if(mi.type != Turtle.MoveType.TOOL_CHANGE) {
				toKeep.add(mi);
				continue;
			}
			// we found a tool change.
			// between this and the next tool change/eof are there any draw commands?
			boolean found=false;
			for(int j=i+1;j<size;++j) {
				Turtle.Movement mj = t.history.get(j);
				if(mj.type == Turtle.MoveType.TOOL_CHANGE) break;
				if(mj.type == Turtle.MoveType.DRAW) {
					found=true;
					break;
				}
			}
			if(found) {
				toKeep.add(mi);
			}
		}
		t.history = toKeep;
	}
	
	/**
	 * 
	 * @return true or false
	 */
	public boolean shouldResizeFill() {
		if(myPanel!=null) return myPanel.shouldResizeFill();
		int result = JOptionPane.showConfirmDialog(null, "Resize to fill margins?", "Resize", JOptionPane.YES_NO_OPTION);
		return (result == JOptionPane.YES_OPTION);
	}

	/**
	 * 
	 * @return true or false
	 */
	public boolean shouldResizeFit() {
		if(myPanel!=null) return myPanel.shouldResizeFit();
		int result = JOptionPane.showConfirmDialog(null, "Resize to fit inside margins?", "Resize", JOptionPane.YES_NO_OPTION);
		return (result == JOptionPane.YES_OPTION);
	}

	/**
	 * 
	 * @return true or false
	 */
	public boolean shouldReorder() {
		if(myPanel!=null) return myPanel.shouldReorder();
		int result = JOptionPane.showConfirmDialog(null, "Avoid needless travel?", "Optimize", JOptionPane.YES_NO_OPTION);
		return (result == JOptionPane.YES_OPTION);
	}
	
	/**
	 * 
	 * @return true or false
	 */
	public boolean shouldFlipV() {
		if(myPanel!=null) return myPanel.shouldFlipV();
		int result = JOptionPane.showConfirmDialog(null, "Flip vertical?", "Flip", JOptionPane.YES_NO_OPTION);
		return (result == JOptionPane.YES_OPTION);
	}
	
	/**
	 * 
	 * @return true or false
	 */
	public boolean shouldFlipH() {
		if(myPanel!=null) return myPanel.shouldFlipH();
		int result = JOptionPane.showConfirmDialog(null, "Flip horizonal?", "Flip", JOptionPane.YES_NO_OPTION);
		return (result == JOptionPane.YES_OPTION);
	}
	
	/**
	 * 
	 * @return true or false
	 */
	public boolean shouldSimplify() {
		if(myPanel!=null) return myPanel.shouldSimplify();
		int result = JOptionPane.showConfirmDialog(null, "Simplify?", "Optimize", JOptionPane.YES_NO_OPTION);
		return (result == JOptionPane.YES_OPTION);
	}
	
	/**
	 * 
	 * @return true or false
	 */
	public boolean shouldCrop() {
		if(myPanel!=null) return myPanel.shouldCrop();
		int result = JOptionPane.showConfirmDialog(null, "Crop to margins?", "Crop", JOptionPane.YES_NO_OPTION);
		return (result == JOptionPane.YES_OPTION);
	}
}
