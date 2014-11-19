/**
 * 
 */
package plugins.davhelle.cellgraph.painters;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.util.HashMap;

import com.vividsolutions.jts.algorithm.ConvexHull;
import com.vividsolutions.jts.awt.ShapeWriter;
import com.vividsolutions.jts.geom.Geometry;

import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.misc.Ellipse;
import plugins.davhelle.cellgraph.nodes.Node;
import icy.canvas.IcyCanvas;
import icy.main.Icy;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.process.EllipseFitter;
import ij.process.ImageProcessor;

/**
 * @author Davide Heller
 *
 */
public class ConvexHullOverlay extends Overlay {

	private SpatioTemporalGraph stGraph;
	private HashMap<Node, EllipseFitter> convexHulls;
	
	public ConvexHullOverlay(SpatioTemporalGraph spatioTemporalGraph) {
		super("Convex hull");
		stGraph = spatioTemporalGraph;
		convexHulls = new HashMap<Node, EllipseFitter>();
		ShapeWriter sw = new ShapeWriter();
		
		for(Node n: stGraph.getFrame(0).vertexSet()){
			
			Geometry g = n.getGeometry();
			Geometry convex_hull = new ConvexHull(g).getConvexHull();
			
			Shape shape = sw.toShape(convex_hull);
			
			ShapeRoi imageJ_roi = new ShapeRoi(shape);
			Roi[] rois = imageJ_roi.getRois();
			
			assert rois.length == 1: "More than one polygon found";
			assert rois[0] instanceof PolygonRoi: "Non polygonal roi found";
			PolygonRoi my_roi = (PolygonRoi)rois[0];
			
			ImageProcessor roi_mask = my_roi.getMask();
			assert roi_mask != null: "No mask defined";
//			
			ImagePlus imp2 = NewImage.createByteImage(
					"New image", 100, 100, 1, NewImage.FILL_BLACK);
			ImageProcessor ip = imp2.getProcessor();
			ip.setRoi(my_roi);
			//imp2.show();
			
			//ImageProcessor ip = imageJ_roi.getMask();
			//ip.setRoi(2, 2, 2, 2);
			
			//follow the hint of in evernote
			
			Rectangle r = ip.getRoi();
			//visualize results
			EllipseFitter ef = new EllipseFitter(); 
			ef.fit(ip,null);
			//ef.drawEllipse(ip);
			//transform this back to a shape somehow
			convexHulls.put(n, ef);	
		}
	}
	
	@Override
    public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
    {
		int time_point = Icy.getMainInterface().getFirstViewer(sequence).getPositionT();

		if(time_point < stGraph.size()){
			//TODO include 3D information (in case of VTK)!
			Color old = g.getColor();
			
			g.setColor(Color.green);
			
			for(Node n: stGraph.getFrame(time_point).vertexSet()){
				if(convexHulls.containsKey(n)){
					EllipseFitter ef = convexHulls.get(n);
					/* 
					 * http://rsb.info.nih.gov/ij/developer/source/ij/process/EllipseFitter.java.html
					 * 
					 * ef.major: major axis 
					 * ef.minor: minor axis
					 * ef.theta:, angle of major axis, clockwise with respect to x axis
					 * 
					 * for complete ellipse drawing see
					 * Draws the ellipse on the specified image.
					 * public void drawEllipse(ImageProcessor ip) 
					 * 
					 * */
					double x0 = ef.xCenter + n.getGeometry().getCentroid().getX();
			        double y0 = ef.yCenter + n.getGeometry().getCentroid().getY();
			        double length = ef.major / 2.0;
			        double x1 = x0 + Math.cos(ef.theta) * length;
			        double y1 = y0 - Math.sin(ef.theta) * length;
					
					g.draw(new Line2D.Double(x0, y0, x1, y1));
				}
				if(g.getColor() == Color.green)
					g.setColor(Color.blue);
				else
					g.setColor(Color.green);
				
			}
			
			g.setColor(old);
		}
    }

}
