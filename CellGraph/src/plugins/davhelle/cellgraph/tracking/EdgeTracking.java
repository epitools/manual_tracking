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
	//TODO transform static methods to object
	
	/**
	 * Tracks all the edges starting from the first frame in the graph
	 * 
	 * @param stGraph the graph whose edges to track
	 * @return An array of presence for each edge. Each array cell 
	 * tells if the edge is present or not at the corresponding time point. The key of the map is
	 * the cantor paring of the edges vertex ids.
	 */
	public static HashMap<Long, boolean[]> trackEdges(
			SpatioTemporalGraph stGraph) {
		HashMap<Long, boolean[]> tracked_edges = new HashMap<Long,boolean[]>();
		
		initializeTrackedEdges(stGraph, tracked_edges,0);
		for(int i=1; i<stGraph.size(); i++)
			analyzeFrame(stGraph, tracked_edges, i);
		
		return tracked_edges;
	}
	
	/**
	 * Tracks all the edges starting from the first frame in the graph
	 * 
	 * @param stGraph the graph whose edges to track
	 * @param plugin calling ezPlug GUI to feedback progress
	 * @return An array of presence for each edge. Each array cell 
	 * tells if the edge is present or not at the corresponding time point. The key of the map is
	 * the cantor paring of the edges vertex ids.
	 */
	public static HashMap<Long, boolean[]> trackEdges(
			SpatioTemporalGraph stGraph, CellOverlay plugin) {
		HashMap<Long, boolean[]> tracked_edges = new HashMap<Long,boolean[]>();
		
		int starting_frame_no = plugin.varT1StartingFrame.getValue();
		
		plugin.getUI().setProgressBarMessage("Tracking Edges..");
		plugin.getUI().setProgressBarValue(0.0);
		
		initializeTrackedEdges(stGraph, tracked_edges,starting_frame_no);
		for(int i=starting_frame_no+1; i<stGraph.size(); i++){
			analyzeFrame(stGraph, tracked_edges, i,starting_frame_no);
			plugin.getUI().setProgressBarValue((double)i/stGraph.size());
		}
		return tracked_edges;
	}

	/**
	 * Initializes the boolean arrays for each edge in the first frame
	 * 
	 * @param stGraph graph to analyze
	 * @param tracked_edges empty map
	 */
	private static void initializeTrackedEdges(
			SpatioTemporalGraph stGraph,
			HashMap<Long, boolean[]> tracked_edges,
			int starting_frame_no) {
		FrameGraph first_frame = stGraph.getFrame(starting_frame_no);
		for(Edge e: first_frame.edgeSet())
			if(e.canBeTracked(first_frame)){
				long track_code = e.getPairCode(first_frame);
				tracked_edges.put(track_code, new boolean[stGraph.size() - starting_frame_no]);
				tracked_edges.get(track_code)[0] = true;
			}
	}
	
	/**
	 * Given the initialized map (see initializeTrackedEdges method)
	 * the analyzeFrame method fills the map for the time point i
	 * by verifying the presence of each included edge at the
	 * frame i of the stGraph. 
	 * 
	 * @param stGraph graph to analyze
	 * @param tracked_edges initialized output map
	 * @param i time point to analyze
	 */
	private static void analyzeFrame(SpatioTemporalGraph stGraph,
			HashMap<Long, boolean[]> tracked_edges, int i, int starting_frame_no) {
		FrameGraph frame_i = stGraph.getFrame(i);
		FrameGraph frame_pre = stGraph.getFrame(i-1);
		trackEdgesInFrame(tracked_edges, frame_i, starting_frame_no);
		removeUntrackedEdges(tracked_edges, frame_i,frame_pre, starting_frame_no);
	}
	
	/**
	 * Checks the presence of edges in frame_i
	 * 
	 * @param tracked_edges presence map
	 * @param frame_i frame to check
	 */
	private static void trackEdgesInFrame(
			HashMap<Long, boolean[]> tracked_edges,
			FrameGraph frame_i,
			int starting_frame_no) {
		
		for(Edge e: frame_i.edgeSet())
			if(e.canBeTracked(frame_i)){
				long edge_track_code = e.getPairCode(frame_i);
				
				if(tracked_edges.containsKey(edge_track_code)){
					
					boolean[] oldArray = tracked_edges.get(edge_track_code);
					
					int correctedFrameNo = frame_i.getFrameNo() - starting_frame_no;
					if(oldArray.length < correctedFrameNo )
						continue;
					else
						tracked_edges.get(edge_track_code)[correctedFrameNo] = true;
			
				}
			}
	}
	
	/**
	 * Eliminates the edges that are not found because 
	 * one of the vertices is not present. Currently used
	 * to differentiate between lost edges of T1 transitions
	 * (i.e. cells are still there but not neighbors anymore) and
	 * lost edges because of missing cells (possible segmentation mistake or elimination) 
	 * 
	 * @param tracked_edges presence map
	 * @param frame_i frame to check
	 */
	private static void removeUntrackedEdges(
			HashMap<Long, boolean[]> tracked_edges, 
			FrameGraph frame_i, 
			FrameGraph frame_pre,
			int starting_frame_no) {
		
		//introduce the difference between lost edge because of tracking and because of T1
		ArrayList<Long> to_eliminate = new ArrayList<Long>();
		ArrayList<Long> to_resize = new ArrayList<Long>();
		
		int i_adjusted = frame_i.getFrameNo() - starting_frame_no;
		int preNo_adjusted = frame_pre.getFrameNo() - starting_frame_no;
		
		for(long track_code:tracked_edges.keySet()){
			
			// skip edge arrays that have been resized
			boolean[] oldArray = tracked_edges.get(track_code);
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
			boolean[] oldArray = tracked_edges.get(track_code);
			tracked_edges.put(track_code, Arrays.copyOfRange(oldArray, 0, preNo_adjusted));
		}
		
		for(long track_code:to_eliminate)
			tracked_edges.remove(track_code);
	}
}
