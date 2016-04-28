package plugins.davhelle.cellgraph.overlays;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D.Double;

import com.vividsolutions.jts.awt.ShapeWriter;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

import icy.canvas.IcyCanvas;
import icy.gui.frame.progress.AnnounceFrame;
import icy.main.Icy;
import icy.sequence.Sequence;
import jxl.write.WritableSheet;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Node;
import plugins.kernel.canvas.VtkCanvas;



public class ManualTrackingOverlay extends StGraphOverlay {
	
	/**
	 * JTS Geometry factory to generate segments to visualize connectivity
	 */
	private GeometryFactory factory;
	/**
	 * JTS to AWT shape converter
	 */
	private ShapeWriter writer;
	

	public ManualTrackingOverlay(SpatioTemporalGraph stGraph) {
		super("Manual Tracking", stGraph);
		
		this.factory = new GeometryFactory();
		this.writer = new ShapeWriter();
		
		new AnnounceFrame("This will be a very cool manual tracking overlay, stay tuned!:-)");
		
	}
	
	@Override
	public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
    {
		if (canvas instanceof VtkCanvas)
			return;
		
		int time_point = Icy.getMainInterface().getFirstViewer(sequence).getPositionT();

		//Take previous time point
		time_point--;
		
		if(time_point >= 0 && time_point < stGraph.size()){
			FrameGraph frame_i = stGraph.getFrame(time_point);
			paintFrame(g, frame_i);
		}
		
		if(super.isLegendVisible())
			paintLegend(g,sequence,canvas);
		
    }

	@Override
	public void paintFrame(Graphics2D g, FrameGraph frame_i) {
		
		for(Node n: frame_i.vertexSet()){
			if(n.hasColorTag()){
				g.setColor(new Color(255,0,0,180)); //last is alpha channel
				g.fill(writer.toShape(n.getGeometry()));
				
				for(Node neighbor: n.getNeighbors()){
					
					//connect to central node
					connectNodes(g, neighbor, n);
					
					//connect nodes among each other
					for(Node other: n.getNeighbors())
						if(neighbor != other)
							if(frame_i.containsEdge(neighbor, other))
								connectNodes(g, neighbor, other);
				}
				
				
			}
		}
		
		//get neighbors of cell and draw the connectivity graph
		//by testing the presence of edges, such to find
		//the circle surrounding the cell that should be tracked
		
		
		
		//TODO add click response for user

	}

	private void connectNodes(Graphics2D g, Node source, Node target) {
		Point a = source.getCentroid();
		Point b = target.getCentroid();
		
		//transform to JTS coordinates
		Coordinate[] edge_vertices = {a.getCoordinate(),b.getCoordinate()};

		//define line
		LineString edge_line = factory.createLineString(edge_vertices);

		//draw line
		g.draw(writer.toShape(edge_line));
		g.draw(writer.toShape(a));
	}

	@Override
	public void specifyLegend(Graphics2D g, Double line) {
		// TODO Auto-generated method stub

	}

	@Override
	void writeFrameSheet(WritableSheet sheet, FrameGraph frame) {
		// TODO Auto-generated method stub

	}

}
