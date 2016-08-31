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
import icy.gui.frame.progress.AnnounceFrame;
import icy.main.Icy;
import icy.sequence.Sequence;
import icy.type.point.Point5D;
import jxl.write.WritableSheet;
import plugins.adufour.ezplug.EzVarEnum;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.io.CsvTrackWriter;
import plugins.davhelle.cellgraph.misc.CellColor;
import plugins.davhelle.cellgraph.nodes.Division;
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
	
	private Node detachedCell;
	private int division_clicks;
	private Node[] division_nodes;
	private boolean repair_mode;
	private String autoSaveDir;
	

	public ManualTrackingOverlay(SpatioTemporalGraph stGraph, 
			EzVarEnum<CellColor> varPolygonColor,
			String autoSaveDir) {
		super("Manual Tracking", stGraph);
		
		this.factory = new GeometryFactory();
		this.writer = new ShapeWriter();
		this.visualizationMode = 2;
		this.varPolygonColor = varPolygonColor;
		
		this.currentlyTrackedCell = null;
		this.currentLegend = "Click on a cell to start tracking it";
		this.insertionLock = false;
		this.setPriority(OverlayPriority.TOPMOST);
		
		this.autoSaveDir = "None";
		if(autoSaveDir != this.autoSaveDir)
			this.autoSaveDir = autoSaveDir;
		division_clicks=-1;
	}
	
	@Override
	public void keyPressed(KeyEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
	{
		if(currentlyTrackedCell != null){
			if (e.getKeyCode() == KeyEvent.VK_SPACE){
				resetTracking(canvas);
			}
			else if(e.getKeyCode() == KeyEvent.VK_P){
				propagateCurrentTrackedCell();
				resetTracking(canvas);
			}
			else if(e.getKeyCode() == KeyEvent.VK_E){
				eliminateCurrentTrackedCell();
				resetTracking(canvas);
			}
			else if(e.getKeyCode() == KeyEvent.VK_D){
				divisionTracking();
				painterChanged();
			}
			else if(e.getKeyCode() == KeyEvent.VK_F){
				insertionLock = false;
				repair_mode = true;
				//TODO expand this as a possibility
				currentLegend = "Fix mode: select daughter cell to repair!";
				painterChanged();
			}
			else if(e.getKeyCode() == KeyEvent.VK_R){
				//REDO
				currentlyTrackedCell = currentlyTrackedCell.getPrevious();
				linkNodes(currentlyTrackedCell.getNext(), detachedCell);
				
				canvas.setPositionT(canvas.getPositionT() - 1);
			}
		}
	}

	private void divisionTracking() {
		// now add two cells
		currentLegend = "Division_mode: select the 1st daughter cell!";
		division_nodes = new Node[2];
		division_clicks = 2;
	}

	private void resetTracking(IcyCanvas canvas) {
		currentlyTrackedCell = null;
		canvas.setPositionT(0);

		insertionLock = false;
		division_clicks = -1;
		
		//automatic saving
		CsvTrackWriter track_writer = new CsvTrackWriter(
				stGraph,autoSaveDir);
		track_writer.write();
		new AnnounceFrame("Automatical saved track: "+autoSaveDir, 5);
		
		currentLegend = "Reset! Click on a cell to start tracking the next cell";
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
			 		
			 		if(division_clicks > 0){
			 			division_nodes[division_clicks-1] = cell;
			 		}
			 		else if(repair_mode){
			 			currentlyTrackedCell = cell;
			 			repair_mode = false;
			 		}
			 		else{
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
			}
			
			if(division_clicks > 0){
				division_clicks--;
				if(division_clicks == 0)
					finalizeDivision();
				else
					currentLegend = "Division_mode: select the 2nd daughter cell!";
			}
			else if(time_point + 1 < stGraph.size()){
				advanceToNextFrame(canvas, time_point);
			}
			else if(time_point == stGraph.size() - 1){
				//1. give modification notice & Ask user to repeat.
				currentLegend = "Tracking Completed! To start next cell press [SPACE]";
				insertionLock = true;
			}
		}
	}

	/**
	 * 
	 */
	private void finalizeDivision() {
		Node mother = currentlyTrackedCell;
		
		//get new tracking ids & propagate
		for(int i=0; i< 2; i++){
			division_nodes[i].setTrackID(stGraph.getNewTrackingId());
			division_nodes[i].setTrackingColor(mother.getTrackingColor());
			division_nodes[i].setFirst(division_nodes[i]);
			currentlyTrackedCell = division_nodes[i];
			propagateCurrentTrackedCell();
		}
		
		Division division = new Division(
				mother, division_nodes[0],
				division_nodes[1]);
		
		System.out.println(division.toString());
		
		currentLegend = "Division registered! The daughter cells have been propagated; " +
				"to fix the track of a daughter press [F] or; " +
				"to restart with another cell [SPACE]";
		currentlyTrackedCell = mother;
		insertionLock = true;
	}

	/**
	 * @param canvas
	 * @param time_point
	 */
	private void advanceToNextFrame(IcyCanvas canvas, int time_point) {
		canvas.setPositionT(time_point + 1);
		currentLegend = "Now: click to link a cell or press "
				+ "P[propagate];"
				+ "E[eliminate];"
				+ "R[redo last];"
				+ "D[division]";
	}
	
	private void linkNodes(Node next, Node previous){
		
		//unlink previous if already linked?
		if(next.hasPrevious()){
			Node oldPrevious = next.getPrevious();
			oldPrevious.setNext(null);
			oldPrevious.setErrorTag(-3);
			detachedCell = oldPrevious;
		}
		else{
			detachedCell = null;
		}
		
		if(previous == null){
			next.setTrackID(-1);
			next.setPrevious(null);
			next.setFirst(null);
			next.setTrackingColor(null);
		}
		else{
			//Attach new track
			next.setTrackID(previous.getTrackID());
			next.setTrackingColor(previous.getTrackingColor());
			next.setFirst(previous.getFirst());
			next.setPrevious(previous);
			
					
			//propagate division
			if(previous.hasObservedDivision()){
				if(!previous.hasNext()){
					//this was a cell dividing in the next frame
					//we are basically canceling the division event
					
					//1. reset division
					//2. cancel division form frame
					//3. propagate cancellation of division
					
				} else {
					// TODO this should be rewritten
					// nobody guaratees that the tracks are still the
					// same, most likely this previous should actually 
					// be stripped from having the division
					// but also the reverse should be guaranteed.
					next.setDivision(previous.getDivision());
				}
			}
			
			if(previous.hasNext()){
				Node oldNext = previous.getNext();
				oldNext.setPrevious(null);
				oldNext.setErrorTag(-2); //TODO automate this by scanning or listening
				oldNext.setTrackingColor(null);
				while(oldNext.hasNext()){
					oldNext = oldNext.getNext();
					oldNext.setTrackingColor(null); //TODO this should be handled in a separate method
				}
			}
			previous.setNext(next);
			previous.setErrorTag(-1);
	
			//TODO make this action automatic as soon as stGraph.tracking_id > 0
			this.stGraph.setTracking(true);
		}
	}
	
	private void propagateCurrentTrackedCell(){
		//update tracking ID for future cells
		Node future = currentlyTrackedCell;
		while(future.hasNext()){
			future = future.getNext();
			future.setTrackID(currentlyTrackedCell.getTrackID());
			future.setFirst(currentlyTrackedCell.getFirst());
			future.setTrackingColor(currentlyTrackedCell.getTrackingColor());
		}

	}
	
	private void eliminateCurrentTrackedCell(){
		
		Node current = currentlyTrackedCell;
		current.setErrorTag(-7); //elimination (TODO create enum for this)

		//detach all further path
		while(current.hasNext()){
			Node next = current.getNext();
			current.setNext(null);
			
			//detach next position
			next.setTrackID(-1);
			next.setPrevious(null);
			next.setFirst(null);
			//TODO set free errorTag maybe
			
			current = next;
		}

	}
	
	@Override
	public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
    {
		if (canvas instanceof VtkCanvas)
			return;
		
		int time_point = Icy.getMainInterface().getFirstViewer(sequence).getPositionT();

//		if(time_point >= 0 && time_point < stGraph.size()){
//			FrameGraph frame_i = stGraph.getFrame(time_point);
//			paintTrackingIds(g, frame_i);
//		}
		
		if(division_clicks == 1){
			g.setColor(Color.BLUE);
			g.fill(writer.toShape(division_nodes[1].getGeometry()));
		}
		
		if(division_clicks == 0){
			g.setColor(Color.BLUE);
			connectNodes(g, division_nodes[0], division_nodes[1]);
		}
		
		//Take previous time point
		if(time_point > 0 && time_point < stGraph.size()){
			time_point--;
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
		//	user wants to correct his assignment. 
		
		//TODO	For this create a field that saves the link which (optional) 
		//	assigned previously. By clicking on any other cell the user switches 
		//	the assignment done. Make sure to save everything which was overwritten 
		//	(e.g. error_Tag too) Once reverted continue normally;
		
		//TODO	Change the Message when the user is going back a frame
		
		//TODO	Allow for skipping a frame (already possible?), maybe alert the user
		//	or just advise him that he is skipping frames when navigating beyond t+1. Keep t overlay!
		
		//TODO	Error tags could be based on instant lookup (e.g. is cell.next in the t+1 frame?)
		if(currentlyTrackedCell != null){
			if(frame_i.containsVertex(currentlyTrackedCell)){
				overlayTrackedCellFrame(g, currentlyTrackedCell);
			}
		}

	}

	private void overlayTrackedCellFrame(Graphics2D g, Node trackedCell) {
		Color userColor = varPolygonColor.getValue().getColor();
		int userR = userColor.getRed();
		int userG = userColor.getGreen();
		int userB = userColor.getBlue();
		int alpha = 180;

		//Show tracked cell completely filled
		Color displayColor = new Color(userR,userG,userB,alpha);
		g.setColor(displayColor);
		g.fill(writer.toShape(trackedCell.getGeometry()));
		//long f3 = System.currentTimeMillis();
		
		//display them all to help identification (scheme 3)
		for(Node cell: trackedCell.getBelongingFrame().vertexSet())
			g.draw(writer.toShape(cell.getGeometry()));
		//f3 = System.currentTimeMillis() - f3;
		//System.out.printf("%d: Visualized Tracking in %d ms\n",System.currentTimeMillis(),f3);

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
		g.draw(writer.toShape(b));
	}

	@Override
	public void specifyLegend(Graphics2D g, Double line) {
		String s = currentLegend;
		Color c = Color.WHITE;
		int offset = 0;

		if(s.contains(";")){
			for(String sub: s.split(";"))
				OverlayUtils.stringColorLegend(g, line, sub, c, 20*offset++);
		}
		else
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
