package plugins.davhelle.cellgraph;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.awt.ShapeWriter;

import icy.canvas.IcyCanvas;
import icy.main.Icy;
import icy.painter.AbstractPainter;
import icy.sequence.Sequence;

public class JtsBorderPolygonPainter extends AbstractPainter{
	
	boolean[] is_border_polygon;
	ArrayList<Polygon> polygons;
	int time_point;
	
	public JtsBorderPolygonPainter(
			ArrayList<Polygon> jts_polygons, LinearRing border, int time_point){
		
		is_border_polygon = new boolean[jts_polygons.size()];
		this.time_point = time_point;
		polygons = jts_polygons;
		
		for(int i=0; i<polygons.size(); i++)
			is_border_polygon[i] = jts_polygons.get(i).intersects(border);
		
	}
	
	@Override
	public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
	{
		//only display when on selected frame
		if(Icy.getMainInterface().getFirstViewer(sequence).getT() == time_point){
			//Initialize painter
			g.setStroke(new BasicStroke(1));
			g.setColor(Color.RED);
			
			ShapeWriter writer = new ShapeWriter();
			
			for(int i = 0; i<polygons.size(); i++){
				if(is_border_polygon[i]){
					Polygon p = polygons.get(i);
					g.fill(writer.toShape(p));
				}
			}

//			while(border_point_it.hasNext()){
//
//				Coordinate border_point = border_point_it.next();
//				
//				//Set polygon color
//				g.setColor(Color.BLUE);
//
//				g.drawOval(
//						(int)border_point.x, 
//						(int)border_point.y,
//						1, 1);
//
//			}
		}
	}

}