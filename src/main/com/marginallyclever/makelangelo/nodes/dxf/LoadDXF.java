package com.marginallyclever.makelangelo.nodes.dxf;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import javax.swing.filechooser.FileNameExtensionFilter;

import org.kabeja.dxf.Bounds;
import org.kabeja.dxf.DXFConstants;
import org.kabeja.dxf.DXFDocument;
import org.kabeja.dxf.DXFEntity;
import org.kabeja.dxf.DXFLayer;
import org.kabeja.dxf.DXFLine;
import org.kabeja.dxf.DXFPolyline;
import org.kabeja.dxf.DXFSpline;
import org.kabeja.dxf.DXFVertex;
import org.kabeja.dxf.helpers.DXFSplineConverter;
import org.kabeja.dxf.helpers.Point;
import org.kabeja.parser.DXFParser;
import org.kabeja.parser.ParseException;
import org.kabeja.parser.Parser;
import org.kabeja.parser.ParserBuilder;

import com.marginallyclever.core.ColorRGB;
import com.marginallyclever.core.Translator;
import com.marginallyclever.core.log.Log;
import com.marginallyclever.core.node.NodeConnectorExistingFile;
import com.marginallyclever.core.turtle.Turtle;
import com.marginallyclever.makelangelo.nodes.LoadFile;
import com.marginallyclever.makelangelo.nodes.TurtleGenerator;

/**
 * Reads in DXF file and converts it to a Turtle.
 * @author Dan Royer
 * @since 7.25.0
 */
public class LoadDXF extends TurtleGenerator implements LoadFile {
	private static FileNameExtensionFilter filter = new FileNameExtensionFilter(Translator.get("LoadDXF.filter"), "dxf");
	private NodeConnectorExistingFile inputFile = new NodeConnectorExistingFile("LoadDXF.inputFile",filter,"");
	
	private double previousX,previousY;
	private double imageCenterX,imageCenterY;
	
	public LoadDXF() {
		super();
		inputs.add(inputFile);
		inputFile.setDescription(Translator.get("LoadDXF.inputFile.tooltip"));
	}
	
	@Override
	public String getName() {
		return Translator.get("LoadDXF.name");
	}
	
	@Override
	public FileNameExtensionFilter getFileNameFilter() {
		return filter;
	}

	@Override
	public boolean canLoad(String filename) {
		String ext = filename.substring(filename.lastIndexOf('.'));
		return (ext.equalsIgnoreCase(".dxf"));
	}

	// count all entities in all layers
	protected void countAllEntities(DXFDocument doc) {
		@SuppressWarnings("unchecked")
		Iterator<DXFLayer> layerIter = (Iterator<DXFLayer>) doc.getDXFLayerIterator();
		int entityTotal = 0;
		while (layerIter.hasNext()) {
			DXFLayer layer = (DXFLayer) layerIter.next();
			int color = layer.getColor();
			Log.message("Found layer " + layer.getName() + "(RGB="+color+")");
			@SuppressWarnings("unchecked")
			Iterator<String> entityIter = (Iterator<String>) layer.getDXFEntityTypeIterator();
			while (entityIter.hasNext()) {
				String entityType = (String) entityIter.next();
				@SuppressWarnings("unchecked")
				List<DXFEntity> entityList = (List<DXFEntity>) layer.getDXFEntities(entityType);
				Log.message("Found " + entityList.size() + " of type " + entityType);
				entityTotal += entityList.size();
			}
		}
		Log.message(entityTotal + " total entities.");
	}

	/**
	 * 
	 * @param in stream from which to read the DXF file
	 * @return true if load is successful.
	 */
	@Override
	public boolean load(InputStream in) {
		Log.message("Loading...");
		Turtle turtle = new Turtle();
		
		// Read in the DXF file
		Parser parser = ParserBuilder.createDefaultParser();
		try {
			parser.parse(in, DXFParser.DEFAULT_ENCODING);
		} catch (ParseException e) {
			e.printStackTrace();
			return false;
		}
		DXFDocument doc = parser.getDocument();
		Bounds bounds = doc.getBounds();
		imageCenterX = (bounds.getMaximumX() + bounds.getMinimumX()) / 2.0;
		imageCenterY = (bounds.getMaximumY() + bounds.getMinimumY()) / 2.0;

		// convert each entity
		@SuppressWarnings("unchecked")
		Iterator<DXFLayer> layerIter = (Iterator<DXFLayer>)doc.getDXFLayerIterator();
		while (layerIter.hasNext()) {
			DXFLayer layer = (DXFLayer) layerIter.next();
			int color = layer.getColor();
			Log.message("Found layer " + layer.getName() + "(color index="+color+")");

			// ignore the color index, DXF is dumb.
			turtle.setColor(new ColorRGB(0,0,0));
			
			// Some DXF layers are empty.  Only write the tool change command if there's something on this layer.
			@SuppressWarnings("unchecked")
			Iterator<String> entityTypeIter = (Iterator<String>)layer.getDXFEntityTypeIterator();

			while (entityTypeIter.hasNext()) {
				String entityType = (String) entityTypeIter.next();
				@SuppressWarnings("unchecked")
				List<DXFEntity> entityList = (List<DXFEntity>)layer.getDXFEntities(entityType);
				Iterator<DXFEntity> iter = entityList.iterator();
				while(iter.hasNext()) {
					parseEntity(turtle,iter.next());
				}
			}
		}

		outputTurtle.setValue(turtle);
		return true;
	}

	protected void parseEntity(Turtle turtle,DXFEntity e) {
		if (e.getType().equals(DXFConstants.ENTITY_TYPE_LINE)) {
			parseDXFLine(turtle,(DXFLine)e);
		} else if (e.getType().equals(DXFConstants.ENTITY_TYPE_SPLINE)) {
			DXFPolyline polyLine = DXFSplineConverter.toDXFPolyline((DXFSpline)e);
			parseDXFPolyline(turtle,polyLine);
		} else if (e.getType().equals(DXFConstants.ENTITY_TYPE_POLYLINE)
				|| e.getType().equals(DXFConstants.ENTITY_TYPE_LWPOLYLINE)) {
			parseDXFPolyline(turtle,(DXFPolyline)e);
		}
	}
	
	protected double distanceFromPrevious(Point p) {
		double dx = previousX - p.getX();
		double dy = previousY - p.getY();
		return dx*dx+dy*dy;
	}
	
	protected double TX(double x) {
		return (x-imageCenterX);
	}
	
	protected double TY(double y) {
		return (y-imageCenterY);
	}
	
	protected void parseDXFLine(Turtle turtle,DXFLine entity) {
		Point start = entity.getStartPoint();
		Point end = entity.getEndPoint();

		double x = TX(start.getX());
		double y = TY(start.getY());
		double x2 = TX(end.getX());
		double y2 = TY(end.getY());
			
		// which end is closer to the previous point?
		double dx = previousX - x;
		double dy = previousY - y;
		double dx2 = previousX - x2;
		double dy2 = previousY - y2;
		if ( dx * dx + dy * dy < dx2 * dx2 + dy2 * dy2 ) {
			parseDXFLineEnds(turtle,x,y,x2,y2);
		} else {
			parseDXFLineEnds(turtle,x2,y2,x,y);
		}
	}
	
	protected void parseDXFLineEnds(Turtle turtle,double x,double y,double x2,double y2) {
		turtle.jumpTo(x,y);
		turtle.moveTo(x2,y2);
		previousX = x2;
		previousY = y2;
	}

	
	protected void parseDXFPolyline(Turtle turtle,DXFPolyline entity) {
		if(entity.isClosed()) {
			// only one end to care about
			parseDXFPolylineForward(turtle,entity);
		} else {
			// which end is closest to the previous (x,y)?
			int n = entity.getVertexCount()-1;
			double x = TX(entity.getVertex(0).getX());
			double y = TY(entity.getVertex(0).getY());
			double x2 = TX(entity.getVertex(n).getX());
			double y2 = TY(entity.getVertex(n).getY());

			// which end is closer to the previous (x,y) ?
			double dx = x - previousX;
			double dy = y - previousY;
			double dx2 = x2 - previousX;
			double dy2 = y2 - previousY;
			if ( dx * dx + dy * dy < dx2 * dx2 + dy2 * dy2 ) {
				// first point is closer
				parseDXFPolylineForward(turtle,entity);
			} else {
				// last point is closer
				parseDXFPolylineBackward(turtle,entity);
			}
		}
	}
	
	protected void parseDXFPolylineForward(Turtle turtle,DXFPolyline entity) {
		boolean first = true;
		int c = entity.getVertexCount();
		int count = c + (entity.isClosed()?1:0);
		DXFVertex v;
		double x,y;
		for (int j = 0; j < count; ++j) {
			v = entity.getVertex(j % c);
			x = TX(v.getX());
			y = TY(v.getY());
			parsePolylineShared(turtle,x,y,first,j<count-1);
			first = false;
		}
	}
	
	protected void parseDXFPolylineBackward(Turtle turtle,DXFPolyline entity) {
		boolean first = true;
		int c = entity.getVertexCount();
		int count = c + (entity.isClosed()?1:0);
		DXFVertex v;
		double x,y;
		for (int j = 0; j < count; ++j) {
			v = entity.getVertex((c*2-1-j) % c);
			x = TX(v.getX());
			y = TY(v.getY());
			parsePolylineShared(turtle,x,y,first,j<count-1);
			first = false;
		}
	}
	
	protected void parsePolylineShared(Turtle turtle,double x,double y,boolean first,boolean notLast) {
		if (first == true) {
			turtle.jumpTo(x,y);
		} else {
			turtle.penDown();
			turtle.moveTo(x,y);
		}
		previousX = x;
		previousY = y;
	}
}
