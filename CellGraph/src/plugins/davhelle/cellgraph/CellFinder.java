package plugins.davhelle.cellgraph;

import icy.canvas.IcyCanvas2D;
import icy.gui.frame.progress.AnnounceFrame;
import icy.main.Icy;
import icy.sequence.Sequence;
import icy.swimmingPool.SwimmingObject;
import icy.swimmingPool.SwimmingPool;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Node;

/**
 * Class to quickly find the location of a coordinate in the image
 * 
 * @author Davide Heller
 *
 */
public class CellFinder extends EzPlug {

	/**
	 * Sequence to find the coordiante in
	 */
	private EzVarSequence varSequence;
	/**
	 * X coordinate set by the user
	 */
	private EzVarInteger varX;
	/**
	 * Y coordinate set by the user
	 */
	private EzVarInteger varY;
	
	/**
	 * Cell ID set by the user
	 */
	private EzVarInteger varCellId;
	
	private SpatioTemporalGraph stGraph;
	
	@Override
	protected void initialize() {
		
		// Retrieve spatio-temporal graph object in Icy swimming pool
		SwimmingPool icySP = Icy.getMainInterface().getSwimmingPool();
		if(icySP.hasObjects("stGraph", true))
			for (SwimmingObject swimmingObject: icySP.getObjects("stGraph", true))
				if ( swimmingObject.getObject() instanceof SpatioTemporalGraph ){
					stGraph = (SpatioTemporalGraph) swimmingObject.getObject();
					if(!stGraph.hasTracking())
						stGraph = null;
				}
			
		if(stGraph == null){
			// legacy mode (x,y coordinate)
			varX = new EzVarInteger("X location [px]");
			super.addEzComponent(varX);
			varY = new EzVarInteger("Y location [px]");
			super.addEzComponent(varY);
			this.getUI().setRunButtonText("Center viewer on [X,Y]");
		} else {
			// tracking mode (cell_id)
			varCellId = new EzVarInteger("Cell ID");
			super.addEzComponent(varCellId);
			this.getUI().setRunButtonText("Center viewer on [Cell ID]");
		}
			
		
		
		this.getUI().setParametersIOVisible(false);
		
		varSequence = new EzVarSequence("Input sequence");
		super.addEzComponent(varSequence);
		

	}

	@Override
	protected void execute() {
		Sequence sequence = varSequence.getValue();
		
		if(sequence == null){
			new AnnounceFrame("Plugin requires active sequence! Please open an image on which to display results");
			return;
		}
		
		if(stGraph == null){
		
			int x = varX.getValue();
			int y = varY.getValue();
			
			IcyCanvas2D canvas2d = (IcyCanvas2D)sequence.getFirstViewer().getCanvas();
			canvas2d.centerOnImage(x, y);
		
		} else {
			
			int cell_id = varCellId.getValue();
			
			IcyCanvas2D canvas2d = (IcyCanvas2D)sequence.getFirstViewer().getCanvas();
			int t = canvas2d.getPositionT();
			
			if(t < stGraph.size()){
				FrameGraph frame_t = stGraph.getFrame(t);
				if(frame_t.hasTrackID(cell_id)){
					
					Node cell = frame_t.getNode(cell_id);
					
					int x = (int)cell.getCentroid().getX();
					int y = (int)cell.getCentroid().getY();
					
					canvas2d.centerOnImage(x, y);
				} else {
					new AnnounceFrame("Cell Id not found!");
					return;
				}
			} else {
				new AnnounceFrame("Current time point is not covered by stGraph");
				return;
			}
			
		}
	}

	
	@Override
	public void clean() {
	}
	
}
