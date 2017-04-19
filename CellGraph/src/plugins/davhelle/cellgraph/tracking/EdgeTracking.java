package plugins.davhelle.cellgraph.tracking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import plugins.davhelle.cellgraph.CellOverlay;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Edge;
import plugins.davhelle.cellgraph.nodes.Node;

/**
 * Class to track all edges contained in the first frame 
 * of a spatio-temporal graph assuming that a cell tracking
 * method has already been applied to the graph <br>
 * 
 * The edge tracking relies on the cell tracking as every
 * edge is identified by a time independent id computed
 * from the two tracking ids of the edge's cells.<br><br>
 * 
 * <b>Warning: Currently only mend for T1 transition detection!</b>
 * 
 * @author Davide Heller
 *
 */
public class EdgeTracking {
	
	HashMap<Long, Long[]> tracked_edges;
	SpatioTemporalGraph stGraph;
	int starting_frame_no;
	
	/**
	 * Initialize the EdgeTracking with two parameters
	 * 
	 * @param stGraph the spatio-temporal graph of which edges to track
	 * @param startingFrameNo the frame at which to start the tracking
	 */
	public EdgeTracking(SpatioTemporalGraph stGraph, int startingFrameNo){
		tracked_edges = new HashMap<Long,Long[]>();
		this.stGraph = stGraph;
		this.starting_frame_no = startingFrameNo;
	}
	
	/**
	 * Tracks all the edges starting from the first frame in the graph
	 * 
	 * @return An array of presence for each edge. Each array cell 
	 * tells if the edge is present or not at the corresponding time point. The key of the map is
	 * the cantor paring of the edges vertex ids.
	 */
	public HashMap<Long, Long[]> trackEdges(){
		
		initializeTrackedEdges();
		for(int i=1; i<stGraph.size(); i++)
			analyzeFrame(i);
		
		return tracked_edges;
	}
	
	/**
	 * Tracks all the edges starting from the first frame in the graph
	 * 
	 * @param plugin calling ezPlug GUI to feedback progress
	 * @return An array of presence for each edge. Each array cell 
	 * tells if the edge is present or not at the corresponding time point. The key of the map is
	 * the cantor paring of the edges vertex ids.
	 */
	public HashMap<Long, Long[]> trackEdges(CellOverlay plugin) {
		
		plugin.getUI().setProgressBarMessage("Tracking Edges..");
		plugin.getUI().setProgressBarValue(0.0);
		
		initializeTrackedEdges();
		for(int i=starting_frame_no+1; i<stGraph.size(); i++){
			analyzeFrame(i);
			plugin.getUI().setProgressBarValue((double)i/stGraph.size());
		}
		return tracked_edges;
	}

	/**
	 * Initializes the boolean arrays for each edge in the first frame
	 * 
	 */
	private void initializeTrackedEdges() {
		FrameGraph first_frame = stGraph.getFrame(starting_frame_no);
		for(Edge e: first_frame.edgeSet())
			if(e.canBeTracked(first_frame)){
				long track_code = e.getPairCode(first_frame);
				tracked_edges.put(track_code, new Long[stGraph.size() - starting_frame_no]);
				tracked_edges.get(track_code)[0] = track_code;
			}
	}
	
	/**
	 * Given the initialized map (see initializeTrackedEdges method)
	 * the analyzeFrame method fills the map for the time point i
	 * by verifying the presence of each included edge at the
	 * frame i of the stGraph. 
	 * 
	 * @param i time point to analyze
	 */
	private void analyzeFrame(int i) {
		FrameGraph frame_i = stGraph.getFrame(i);
		FrameGraph frame_pre = stGraph.getFrame(i-1);
		trackEdgesInFrame(frame_i);
		removeUntrackedEdges(frame_i,frame_pre);
	}
	
	/**
	 * Checks the presence of edges in frame_i
	 * 
	 * @param frame_i frame to check
	 */
	private void trackEdgesInFrame(FrameGraph frame_i) {
		
		for(Edge e: frame_i.edgeSet())
			if(e.canBeTracked(frame_i)){
				long edge_track_code = e.getPairCode(frame_i);
				
				if(tracked_edges.containsKey(edge_track_code)){
					
					Long[] oldArray = tracked_edges.get(edge_track_code);
					
					int correctedFrameNo = frame_i.getFrameNo() - starting_frame_no;
					if(oldArray.length < correctedFrameNo )
						continue;
					else
						tracked_edges.get(edge_track_code)[correctedFrameNo] = edge_track_code;
			
				}
			} else {
				//Check for possible divisions
			}
	}
	
	/**
	 * Eliminates the edges that are not found because 
	 * one of the vertices is not present. Currently used
	 * to differentiate between lost edges of T1 transitions
	 * (i.e. cells are still there but not neighbors anymore) and
	 * lost edges because of missing cells (possible segmentation mistake or elimination) 
	 * 
	 * @param frame_i frame to check
	 * @param frame_pre frame previous to the frame to check
	 */
	private void removeUntrackedEdges(
			FrameGraph frame_i, 
			FrameGraph frame_pre) {
		
		//introduce the difference between lost edge because of tracking and because of T1
		ArrayList<Long> to_eliminate = new ArrayList<Long>();
		ArrayList<Long> to_resize = new ArrayList<Long>();
		
		int i_adjusted = frame_i.getFrameNo() - starting_frame_no;
		int preNo_adjusted = frame_pre.getFrameNo() - starting_frame_no;
		
		for(long track_code:tracked_edges.keySet()){
			
			// skip edge arrays that have been resized
			Long[] oldArray = tracked_edges.get(track_code);
			if(oldArray.length < i_adjusted )
				continue;
			
			for(int track_id: Edge.getCodePair(track_code)) //Edge tuple (v1,v2)
				if(!frame_i.hasTrackID(track_id)){ // vertex is missing
					
					// check previous frames whether cell is on the boundary
					assert frame_pre.hasTrackID(track_id): String.format(
							"Missing cell %d at frame %d",track_id, preNo_adjusted + starting_frame_no);
					Node pre = frame_pre.getNode(track_id);
					
					if(pre.onBoundary())
						to_resize.add(track_code); 
					else 
						to_eliminate.add(track_code);
					
					break;
				}
		}
		
		// re-scale boolean array in case the cell just went out from the boundary
		for(long track_code:to_resize){
			Long[] oldArray = tracked_edges.get(track_code);
			tracked_edges.put(track_code, Arrays.copyOfRange(oldArray, 0, preNo_adjusted));
		}
		
		for(long track_code:to_eliminate)
			tracked_edges.remove(track_code);
	}
}
