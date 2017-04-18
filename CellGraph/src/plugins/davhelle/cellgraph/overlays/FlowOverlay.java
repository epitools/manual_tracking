package plugins.davhelle.cellgraph.overlays;

import icy.gui.dialog.SaveDialog;
import icy.gui.frame.progress.AnnounceFrame;
import icy.system.IcyExceptionHandler;
import icy.util.XMLUtil;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.geom.Line2D.Double;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jxl.write.WritableSheet;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.misc.CatmullRom;
import plugins.davhelle.cellgraph.nodes.Node;

import com.vividsolutions.jts.awt.ShapeWriter;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;

/**
 * Generate smooth view of the off cell center path.
 * 
 * Automatically exports path to XML format compatible with TrackManager.
 * 
 * [STILL IN TEST PHASE, NOT ACCESSIBLE THROUGH MAIN GUI!]
 * 
 * @author Davide Heller
 *
 */
public class FlowOverlay extends StGraphOverlay {
	
	public static final String DESCRIPTION =
			"Overlay representing the tracking positions of cell centers<br/>" +
			"Choose from one of the following modes:<br/>" +
			"0 - raw coordinates<br/>" +
			"1 - smoothened (CatmullRom)<br/>" +
			"2 - simplified (Douglas-Peucker)";
	
	HashMap<Node,LineString> rawFlow = new HashMap<Node,LineString>();
	HashMap<Node,Geometry> simpleFlow = new HashMap<Node, Geometry>();
	HashMap<Node,LineString> smoothFlow = new HashMap<Node, LineString>();
	
	//Default values
	private int smooth_interval = 10;
	private int flow_paint_style;
	
	ShapeWriter writer = new ShapeWriter();

	public FlowOverlay(SpatioTemporalGraph stGraph, int flow_mode) {
		super(String.format("Flow over time (%d)",flow_mode), stGraph);
		
		this.flow_paint_style = flow_mode;

		GeometryFactory factory = new GeometryFactory();
		
		FrameGraph frame = stGraph.getFrame(0);

		for(Node n: frame.vertexSet()){

			if(!n.hasNext())
				continue;

			ArrayList<Coordinate> list = new ArrayList<Coordinate>();

			list.add(n.getCentroid().getCoordinate());
			
			Node next = n;
			while(next.hasNext()){
				next = next.getNext();
				list.add(next.getCentroid().getCoordinate());
			}

			LineString cell_path = factory.createLineString(
					list.toArray(new Coordinate[list.size()]));

			rawFlow.put(n,cell_path);
			
            Geometry simple = DouglasPeuckerSimplifier.simplify(cell_path, 3.0);
            if (simple.getCoordinates().length > 2)
            		simpleFlow.put(n, simple);
            
            List<Coordinate> raw = new ArrayList<Coordinate>();
            
            for(int j=0; j < list.size()-1; j+=smooth_interval){
            		raw.add(list.get(j));
            }
            
            raw.add(list.get(list.size() - 1));
            //raw.addAll(Arrays.asList(simple.getCoordinates()));
            
            List<Coordinate> spline = new ArrayList<Coordinate>(); 
            
            try {
				spline = CatmullRom.interpolate(raw, smooth_interval + 1);
			} catch (Exception e) {
				e.printStackTrace();
			}
            
            if(spline.size() > 2){
            		LineString smooth_path = factory.createLineString(
            				spline.toArray(new Coordinate[spline.size()]));
            		
            		smoothFlow.put(n, smooth_path);
            }
            
		}
		
	}

	@Override
	public void paintFrame(Graphics2D g, FrameGraph frame_i) {
		
		for(Node n: frame_i.vertexSet()){

			n=n.getFirst();

			switch( flow_paint_style ){

			case 0:
				if(rawFlow.containsKey(n)){
					LineString s = rawFlow.get(n);
					Shape flow = writer.toShape(s);
					g.setColor(Color.cyan);
					g.draw(flow);
				}
				break;
			case 1:
				if(smoothFlow.containsKey(n)){
					LineString s = smoothFlow.get(n);
					Shape flow = writer.toShape(s);
					g.setColor(Color.blue);
					g.draw(flow);
				}
				break;
			case 2:
				if(simpleFlow.containsKey(n)){
					Geometry s = simpleFlow.get(n);
					Shape flow = writer.toShape(s);
					g.setColor(Color.green);
					g.draw(flow);
				}
				break;
			default:
				continue;
			}
		}
			

	}
	
	/**
	 * save the particleArrayList in XML.
	 * adapted from 
	 *  - plugins.fab.trackgenerator.BenchmarkSequence
	 *  - plugins.fab.trackmanager.TrackManager
	 * 
	 * @param XMLFile
	 */
	public void saveXML( File XMLFile )
	{
		
		HashMap<Node,LineString> trackToExport = rawFlow;
		if(flow_paint_style == 1)
			trackToExport = smoothFlow;
		
		Document document = XMLUtil.createDocument( true );
		Element documentElement = document.getDocumentElement();
		
		Element versionElement = XMLUtil.addElement( 
				documentElement , "trackfile" ); 
		versionElement.setAttribute("version", "1");

		Element trackGroupElement = XMLUtil.addElement( 
				documentElement , "trackgroup" );
		
		for(Node n: stGraph.getFrame(0).vertexSet()){

			if(!trackToExport.containsKey(n))
				continue;
			
			Element trackElement = XMLUtil.addElement( 
					trackGroupElement , "track" );				
			XMLUtil.setAttributeIntValue( 
					trackElement , "id" , n.getTrackID() );
			
			Coordinate[] track = trackToExport.get(n).getCoordinates();
			int track_length = track.length;
			
			for(int i=0; i < track_length; i++){
				
				Element detection = document.createElement("detection");
				trackElement.appendChild( detection );
				
				Coordinate centroid = track[i];
				
				double x = roundDecimals2(centroid.x);
				double y = roundDecimals2(centroid.y);
				
				XMLUtil.setAttributeDoubleValue( detection , "x" , x );
				XMLUtil.setAttributeDoubleValue( detection , "y" , y );
				XMLUtil.setAttributeIntValue( detection , "t" , i );
				
			}
				
		}
		
		XMLUtil.saveDocument( document , XMLFile );
	}
	
	/**
	 * from plugins.fab.trackgenerator.BenchmarkSequence
	 * @param value
	 * @return
	 */
	private double roundDecimals2(double value) {
		value = value * 1000d;
		value = Math.round(value);
		return value/1000d;		
	}

	@Override
	public void specifyLegend(Graphics2D g, Double line) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		
		if (flow_paint_style > 1)
			new AnnounceFrame("XML export not yet implemented for mode "+flow_paint_style);
		else{
			try {
				String file_name = SaveDialog.chooseFile(
						"Please choose where to save the XML track",
						"~/Desktop",
						"cell_track", ".xml");
				
				if(file_name == null)
					return;

				saveXML(new File(file_name));

				new AnnounceFrame("XML file exported successfully to: "+file_name,10);

			} 
			catch (Exception xmlException) {
				IcyExceptionHandler.showErrorMessage(xmlException, true, true);
			}
		}
	}

	@Override
	void writeFrameSheet(WritableSheet sheet, FrameGraph frame) {
		// TODO Auto-generated method stub

	}

}
