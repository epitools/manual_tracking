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
import plugins.adufour.ezplug.EzVarEnum;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.misc.CellColor;
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
	private String currentLegend;
	private boolean insertionLock;
	EzVarEnum<CellColor> 		varPolygonColor;
	

	public ManualTrackingOverlay(SpatioTemporalGraph stGraph, EzVarEnum<CellColor> varPolygonColor) {
		super("Manual Tracking", stGraph);
		
		this.factory = new GeometryFactory();
		this.writer = new ShapeWriter();
		this.visualizationMode = 2;
		this.varPolygonColor = varPolygonColor;
		
		this.currentlyTrackedCell = null;
		this.currentLegend = "Click on a cell to start tracking it";
		this.insertionLock = false;
	}
	
	@Override
	public void keyPressed(KeyEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
	{
		if (e.getKeyCode() == KeyEvent.VK_SPACE){
			currentlyTrackedCell = null;
			
			canvas.setPositionT(0);

			insertionLock = false;
			currentLegend = "Reset! Click on a cell to start tracking the next cell";
		}
		else (e.getKeyCode() == KeyEvent.VK_ENTER){
			propagateCurrentTrackedCell();

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
			 			cell.setTrackID(stGraph.getNewTrackingId());
			 		
			 		//update
			 		currentlyTrackedCell = cell;
			 	}
			}
			
			if(time_point + 1 < stGraph.size()){
				canvas.setPositionT(time_point + 1);
				currentLegend = "Now click on the best matching cell [press SPACE to restart (ENTER to propagate & restart) ]";
			}
			
			if(time_point == stGraph.size() - 1){
				//1. give modification notice & Ask user to repeat.
				currentLegend = "Tracking Completed! To start next cell press [SPACE]";
				insertionLock = true;
			}
		}
	}
	
	private void linkNodes(Node next, Node previous){
		
		//unlink previous if already linked?
		if(next.hasPrevious()){
			Node oldPrevious = next.getPrevious();
			oldPrevious.setNext(null);
		}
		
		//Attach new track
		next.setTrackID(previous.getTrackID());
		next.setFirst(previous.getFirst());
		next.setPrevious(previous);
		
				
		//propagate division
		if(previous.hasObservedDivision())
			next.setDivision(previous.getDivision());

		previous.setNext(next);
		previous.setErrorTag(-1);

		this.stGraph.setTracking(true);
	}
	
	private void propagateCurrentTrackedCell(){
		//update tracking ID for future cells
		Node future = currentlyTrackedCell;
		while(future.hasNext()){
			future = future.getNext();
			future.setTrackID(currentlyTrackedCell.getTrackID());
			future.setFirst(currentlyTrackedCell.getFirst());
		}

	}
	
	@Override
	public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
    {
		if (canvas instanceof VtkCanvas)
			return;
		
		int time_point = Icy.getMainInterface().getFirstViewer(sequence).getPositionT();

		if(time_point >= 0 && time_point < stGraph.size()){
			FrameGraph frame_i = stGraph.getFrame(time_point);
			//paintTrackingIds(g, frame_i);
		}
		
		//Take previous time point
		time_point--;
		
		if(time_point >= 0 && time_point < stGraph.size()){
			FrameGraph frame_i = stGraph.getFrame(time_point);
			paintFrame(g, frame_i);
		}
		
		if(super.isLegendVisible())
			paintLegend(g,sequence,canvas);
		
    }
	
	public void paintTrackingIds(Graphics2D g, FrameGraph frame_i)
	{

		int fontSize = 20;
		g.setFont(new Font("TimesRoman", Font.PLAIN, fontSize));
		g.setColor(Color.CYAN);

		for(Node cell: frame_i.vertexSet()){

			if(cell.getTrackID() != -1){ 

				Coordinate centroid = cell.getCentroid().getCoordinate();

				g.drawString(Integer.toString(cell.getTrackID()), 
						(float)centroid.x - 2  , 
						(float)centroid.y + 2);
			}

		}		
	}

	@Override
	public void paintFrame(Graphics2D g, FrameGraph frame_i) {
		
		//TODO	compare frame_i time point with the one of currentlyTrackedCell
		//	if the time point is immediately before, then assume that the 
		//TODO	user wants to correct his assignment. For this create a field
		//	that saves the link which (optional) assigned previously. By
		//	clicking on any other cell the user switches the assignment
		//	done. Make sure to save everythig which was overritten (e.g. error_Tag too)
		//	Once reverted continue normally;
		//TODO	Change the Message when the user is going back a frame
		//TODO	Allow for skipping a frame (already possible?), maybe allert the user
		//	or just advise him that he is skipping frames when navigating beyond t+1. Keep t overlay!
		//TODO	Error tags could be based on instant lookup (e.g. is cell.next in the t+1 frame?)
		
		if(frame_i.containsVertex(currentlyTrackedCell)){
				
			Color userColor = varPolygonColor.getValue().getColor();
			int userR = userColor.getRed();
			int userG = userColor.getGreen();
			int userB = userColor.getBlue();
			int alpha = 180;

			//Show tracked cell completely filled
			Color displayColor = new Color(userR,userG,userB,alpha);
			g.setColor(displayColor);
			g.fill(writer.toShape(currentlyTrackedCell.getGeometry()));
			//long f3 = System.currentTimeMillis();
			
			//display them all to help identification (scheme 3)
			for(Node cell: frame_i.vertexSet())
				g.draw(writer.toShape(cell.getGeometry()));
			
			//f3 = System.currentTimeMillis() - f3;
			//System.out.printf("%d: Visualized Tracking in %d ms\n",System.currentTimeMillis(),f3);
		}

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
