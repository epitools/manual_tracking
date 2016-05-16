package plugins.davhelle.cellgraph.overlays;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D.Double;
import java.awt.geom.Point2D;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.vividsolutions.jts.awt.ShapeWriter;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

import icy.canvas.IcyCanvas;
import icy.main.Icy;
import icy.sequence.Sequence;
import icy.type.point.Point5D;
import jxl.write.WritableSheet;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Node;
import plugins.kernel.canvas.VtkCanvas;



public class ManualTrackingOverlay extends StGraphOverlay {
	
	private static final String CHANGE_MODE = "Change Mode";
	/**
	 * JTS Geometry factory to generate segments to visualize connectivity
	 */
	private GeometryFactory factory;
	/**
	 * JTS to AWT shape converter
	 */
	private ShapeWriter writer;
	private int visualizationMode;
	private Node currentlyTrackedCell;
	private int currentlyTrackedId;
	private String currentLegend;
	private boolean insertionLock;
	

	public ManualTrackingOverlay(SpatioTemporalGraph stGraph) {
		super("Manual Tracking", stGraph);
		
		this.factory = new GeometryFactory();
		this.writer = new ShapeWriter();
		this.visualizationMode = 0;
		
		//find highest currently used tracking id
		currentlyTrackedId = 0;
		for(Node n: stGraph.getFrame(0).vertexSet())
			if(n.getTrackID() >= currentlyTrackedId)
				currentlyTrackedId = n.getTrackID() + 1;
		
		this.currentlyTrackedCell = null;
		this.currentLegend = "Click on a cell to start tracking it";
		this.insertionLock = false;
	}
	
	@Override
	public void keyPressed(KeyEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
	{
		if (e.getKeyCode() == KeyEvent.VK_SPACE){
			currentlyTrackedId++;
			currentlyTrackedCell = null;
			
			canvas.setPositionT(0);

			insertionLock = false;
			currentLegend = "Reset! Click on a cell to start tracking the next cell";
		}
	}
	
	@Override
	public void mouseClick(MouseEvent e, Point2D imagePoint, IcyCanvas canvas){
		int time_point = canvas.getPositionT();
		
		if(insertionLock)
			return;
		
		if(time_point < stGraph.size()){
			
			//create point Geometry
			Coordinate point_coor = new Coordinate(imagePoint.getX(), imagePoint.getY());
			Point point_geometry = factory.createPoint(point_coor);			
			
			FrameGraph frame_i = stGraph.getFrame(time_point);
			for(Node cell: frame_i.vertexSet()){
			 	if(cell.getGeometry().contains(point_geometry)){
			 		
			 		//insert currently displayed one
			 		//to establish connection 
			 			
			 		if(currentlyTrackedCell != null)
			 			linkNodes(cell,currentlyTrackedCell);
			 		else
			 			cell.setTrackID(currentlyTrackedId);
			 	}
			}
			
			if(time_point + 1 < stGraph.size()){
				canvas.setPositionT(time_point + 1);
				currentLegend = "Now click on the best matching cell";
			}
			
			if(time_point == stGraph.size() - 1){
				//1. give modification notice & Ask user to repeat.
				currentLegend = "Tracking Completed! To start next cell press [SPACE]";
				insertionLock = true;
			}
		}
	}
	
	private void linkNodes(Node next, Node previous){
		
		next.setTrackID(previous.getTrackID());
		next.setFirst(previous.getFirst());
		next.setPrevious(previous);

		//propagate division
		if(previous.hasObservedDivision())
			next.setDivision(previous.getDivision());

		previous.setNext(next);

		this.stGraph.setTracking(true);
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
			if(n.getTrackID() == 1){
				
				currentlyTracked = n;
				
				g.setColor(new Color(255,0,255,180)); //last is alpha channel
				g.fill(writer.toShape(n.getGeometry()));
				
				for(Node neighbor: n.getNeighbors()){
					
					switch(visualizationMode){
					case 0:
						//just outline first order neighbor ring
						g.setColor(new Color(0,0,255,180)); //last is alpha channel
						g.draw(writer.toShape(neighbor.getGeometry()));
						
						g.setColor(new Color(0,255,0,180));
						//connect to central node
						connectNodes(g, neighbor, n);
						
						//connect nodes among each other
						for(Node other: n.getNeighbors())
							if(neighbor != other)
								if(frame_i.containsEdge(neighbor, other))
									connectNodes(g, neighbor, other);
						break;
					case 1:
						//idea: only neighbor's neighbors that are 
						//also neighbors of other n's neighbors
						//limits amount of lines
						for(Node nn: neighbor.getNeighbors())
							if(frame_i.containsEdge(neighbor, nn))
								connectNodes(g, neighbor, nn);
						break;
					case 2:
						//display them all
						for(Node cell: frame_i.vertexSet())
							g.draw((cell.toShape()));
						break;
					default:
						//Nothing so far
						System.out.println("No default option yet");
					}
				}
			}
		}
		
		//get neighbors of cell and draw the connectivity graph
		//by testing the presence of edges, such to find
		//the circle surrounding the cell that should be tracked
		
		//add the track id!
		
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
		String s = currentLegend;
		Color c = Color.WHITE;
		int offset = 0;

		OverlayUtils.stringColorLegend(g, line, s, c, offset);
	}

	@Override
	void writeFrameSheet(WritableSheet sheet, FrameGraph frame) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public JPanel getOptionsPanel() {
		
		JPanel optionPanel = new JPanel(new GridBagLayout());
		
		addOptionButton(optionPanel, 
				CHANGE_MODE, "Change tracking visualization ");
		
        return optionPanel;
		
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		
		String cmd_string =e.getActionCommand(); 
		
		if(cmd_string.equals(CHANGE_MODE)){
			visualizationMode = (visualizationMode + 1) % 3;
			painterChanged();
		}
	}

	/**
	 * @param optionPanel
	 * @param button_text
	 * @param button_description
	 */
	private void addOptionButton(JPanel optionPanel, 
			String button_text,
			String button_description) {
		
		GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 10, 2, 5);
        gbc.fill = GridBagConstraints.BOTH;
		optionPanel.add(new JLabel(button_description), gbc);
        
        gbc.weightx = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        
		JButton Roi_Button = new JButton(button_text);
        Roi_Button.addActionListener(this);
        optionPanel.add(Roi_Button,gbc);
	}

}
