package plugins.davhelle.cellgraph.overlays;

import icy.util.XLSUtil;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D.Double;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import jxl.write.WritableSheet;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Division;
import plugins.davhelle.cellgraph.nodes.Node;

import com.vividsolutions.jts.awt.ShapeWriter;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 * Given the first frame as reference, a random color
 * is assigned to every cell and is maintained for all the
 * linked cells in the successive frames analyzed. 
 * 
 * @author Davide Heller
 *
 */
public class TrackingOverlay extends StGraphOverlay{
	
	/**
	 * Description string for GUI
	 */
	public static final String DESCRIPTION = 
			"Overlay to review the tracking in case the default overlay<br/>" +
			" has been eliminated or to highlight different aspects<br/><br/>" +
			"The TrackColor that defines uniquely every cell can be used to<br/>" +
			" either outline or fill the cell polygon. The additional<br/>" +
			" information is added complementarily.<br/><br/>" +
			"Additional color codes:<br/><ul>" +
			"<li> [red] cell missing in previous frame" +
			"<li> [yellow] cell missing in next frame" +
			"<li> [green] cell missing in previous&next" +
			"<li> [blue] cell dividing in next frame" +
			"<li> [magenta] brother cell missing" +
			"<li> [cyan] cell eliminated in next frame" +
			"<li> [gray] brother cell was eliminated</ul>";
	
	/**
	 * A Random bright color for each cell
	 */
	private HashMap<Node,Shape> inner_circle;
	private ShapeWriter writer;
	
	/**
	 * A static color map for each error type
	 */
	public static final Map<Integer, Color> errorMap;
	static{
		Map<Integer, Color> aMap = new HashMap<Integer, Color>();
		aMap.put(-2, Color.red);		//cell missing in previous frame
		aMap.put(-3, Color.yellow);		//cell missing in next frame
		aMap.put(-4, Color.green);		//cell missing in previous&next
		aMap.put(-5, Color.blue);		//cell dividing in next frame
		aMap.put(-6, Color.magenta);	//brother cell missing
		aMap.put(-7, Color.cyan);   	//cell eliminated in next frame
		aMap.put(-8, Color.lightGray); 	//brother cell was eliminated
		errorMap = Collections.unmodifiableMap(aMap);
	}
	
	/**
	 * Flag whether to highlight mistakes or fill the cells
	 */
	private boolean highlightMistakes;
	/**
	 * Flag to show frame related tracking statistics
	 */
	private boolean SHOW_STATISTICS = false;

	/**
	 * Initializes the Tracking overlay
	 * 
	 * @param stGraph graph to be analyzed
	 * @param highlightMistakes set true for tracking events to be highlighted/ false to fill cells with their assigned color
	 */
	public TrackingOverlay(SpatioTemporalGraph stGraph, Boolean highlightMistakes) {
		super("Cell Tracking Color",stGraph);
		
		//Color for each cell line
		this.stGraph = stGraph;
		this.highlightMistakes = highlightMistakes.booleanValue();
		this.inner_circle = new HashMap<Node,Shape>();
		this.writer = new ShapeWriter();
		
		//Assign color to cell line starting from first cell
		Iterator<Node> cell_it = stGraph.getFrame(0).iterator();
		Random rand = new Random();
		
		//Assign to every first cell a random color, also 
		//attach the same color to ev. children
		
		while(cell_it.hasNext()){
			Node cell = cell_it.next();
			Color cell_color = newColor(rand);
			cell.setTrackingColor(cell_color);
			while(cell.hasNext()){
				cell = cell.getNext();
				cell.setTrackingColor(cell_color);
			}
		}
		
		checkDivisions();
		
		//Precompute shapes
		for(int i=0; i < stGraph.size(); i++)
			for(Node cell: stGraph.getFrame(i).vertexSet())
				if(cell.getTrackID() != -1)
					computeInnerCircle(cell);
		
	}

	/**
	 * @param cell
	 */
	private void computeInnerCircle(Node cell) {
		Geometry geo = cell.getGeometry();
		Geometry inner_geo = geo.difference(geo.buffer(-3.0));
		inner_circle.put(cell, writer.toShape(inner_geo));
		//TODO return geometry
	}
	
	/**
	 * Generate random color for cell
	 * 
	 * @param rand Random number generator
	 * @return random bright color
	 */
	private Color newColor(Random rand){
		float r_idx = rand.nextFloat();
		float g_idx = rand.nextFloat();
		float b_idx = rand.nextFloat();      

		Color cell_color = new Color(r_idx, g_idx, b_idx);
		cell_color.brighter();
		
		
		return cell_color;
	}
	
	public void checkDivisions(){
		Iterator<Node> cell_it = stGraph.getFrame(0).iterator();
		
		while(cell_it.hasNext()){
			
			Node cell = cell_it.next();
			Color cell_color = cell.getTrackingColor();
			
			if(cell.hasObservedDivision()){
				Division division = cell.getDivision();
				//same color for children or cell_color = newColor(rand);
				Node child1 = division.getChild1();
				Node child2 = division.getChild2();
				
				child1.setTrackingColor(cell_color);
				child2.setTrackingColor(cell_color);
				
				while(child1.hasNext()){
					child1 = child1.getNext();
					child1.setTrackingColor(cell_color);
				}
				
				while(child2.hasNext()){
					child2 = child2.getNext();
					child2.setTrackingColor(cell_color);
				}
				
			}
		}
	}
	
	@Override
	public void paintFrame(Graphics2D g, FrameGraph frame)
	{

		double percentage_tracked = 0;
		for(Node cell: frame.vertexSet()){

			if(cell.getTrackID() != -1){
				if(cell.hasTrackingColor()){
					percentage_tracked++;
					//cell is part of registered correspondence
					g.setColor(cell.getTrackingColor());

					if(highlightMistakes){
						if(!inner_circle.containsKey(cell))
							computeInnerCircle(cell);

						Shape inner = inner_circle.get(cell);
						g.fill(inner);
					}
					else
						g.fill(cell.toShape());


					Point lost = cell.getCentroid();

					if(errorMap.containsKey(cell.getErrorTag())){
						g.setColor(errorMap.get(cell.getErrorTag()));

//						if(highlightMistakes)
//							g.fill(cell.toShape());
//						else
//							g.draw(cell.toShape());

						g.fillOval((int)lost.getX() - 3,(int)lost.getY() - 10, 10, 10);
					}

				}
				else{
					//no tracking found
					g.setColor(Color.white);
					g.draw(cell.toShape());

					Point lost = cell.getCentroid();

					if(errorMap.containsKey(cell.getErrorTag())){

						g.setColor(errorMap.get(cell.getErrorTag()));

//						if(highlightMistakes)
//							g.fill(cell.toShape());
//						else
//							g.draw(cell.toShape());

						g.fillOval((int)lost.getX() - 3,(int)lost.getY() - 10, 10, 10);
					}
				}
			}
			else{
				//Mark cells in green which do have all neighbors tracked
				//and are not on the boundary
				if(!cell.onBoundary()){
					g.setColor(Color.green);

					boolean all_assigned = true;
					for(Node neighbor: cell.getNeighbors())
						if(neighbor.getTrackID() == -1)
							all_assigned = false;

					if(all_assigned)
						g.fill(cell.toShape());
				}
			}
		}

		percentage_tracked = (percentage_tracked/stGraph.getFrame(0).size())*100;

		
		//Statistics Text headline
		if(SHOW_STATISTICS){
			g.setFont(new Font("TimesRoman", Font.PLAIN, 10));

			g.setColor(Color.white);
			g.drawString("Tracked cells: "+(int)percentage_tracked+"%", 10 , 20);

			g.setColor(Color.red);
			g.drawString("previous", 10 , 30);

			g.setColor(Color.yellow);
			g.drawString("next", 60 , 30);

			g.setColor(Color.green);
			g.drawString("none", 90 , 30);
		}
	}

	@Override
	void writeFrameSheet(WritableSheet sheet, FrameGraph frame) {
		XLSUtil.setCellString(sheet, 0, 0, "Cell id");
		XLSUtil.setCellString(sheet, 1, 0, "Centroid x");
		XLSUtil.setCellString(sheet, 2, 0, "Centroid y");
		XLSUtil.setCellString(sheet, 3, 0, "Cell area");

		int row_no = 1;
		for(Node node: frame.vertexSet()){
			if(node.hasNext() || node.hasPrevious()){
				XLSUtil.setCellNumber(sheet, 0, row_no, node.getTrackID());
				XLSUtil.setCellNumber(sheet, 1, row_no, node.getCentroid().getX());
				XLSUtil.setCellNumber(sheet, 2, row_no, node.getCentroid().getY());
				XLSUtil.setCellNumber(sheet, 3, row_no, node.getGeometry().getArea());
				row_no++;
			}
		}

	}

	@Override
	public void specifyLegend(Graphics2D g, Double line) {
		
		String s = "Missing in previous frame";
		Color c = Color.RED;
		int offset = 0;

		OverlayUtils.stringColorLegend(g, line, s, c, offset);

		s = "Missing in next frame";
		c = Color.YELLOW;
		offset = 20;

		OverlayUtils.stringColorLegend(g, line, s, c, offset);
		
		s = "Missing in previous&next";
		c = Color.GREEN;
		offset = 40;

		OverlayUtils.stringColorLegend(g, line, s, c, offset);
		
		s = "Dividing in next frame";
		c = Color.BLUE;
		offset = 60;

		OverlayUtils.stringColorLegend(g, line, s, c, offset);
		
		s = "Sibling missing";
		c = Color.MAGENTA;
		offset = 80;

		OverlayUtils.stringColorLegend(g, line, s, c, offset);
		
		s = "Eliminated in next frame";
		c = Color.CYAN;
		offset = 100;

		OverlayUtils.stringColorLegend(g, line, s, c, offset);
		
	}

}
