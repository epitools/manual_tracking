package plugins.davhelle.cellgraph.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Edge;
import plugins.davhelle.cellgraph.nodes.Node;

import com.vividsolutions.jts.geom.Geometry;

/**
 * Class to represent the gain and loss dynamics of edges between cells
 * 
 * @author Davide Heller
 *
 */
public class T1Transition {
	
	private SpatioTemporalGraph stGraph;

	/**
	 * Track Id's of nodes that will
	 * respectively loose or win a 
	 * neighbor relationship. 
	 */
	int[] loserNodes;
	int[] winnerNodes;
	
	/**
	 * For every time point store the
	 * presence or absence of the old
	 * edge
	 */
	Long[] lostEdgeTrack;
	
	/**
	 * Store when the first stable transition
	 * Occurs. 
	 */
	int detectionTimePoint;

	/**
	 * Store for how long the new edge
	 * could be observed consecutively
	 */
	int transitionLength;
	
	/**
	 * Store the amount of time points
	 * where the old edge could be
	 * detected 
	 */
	int oldEdgeSurvivalLength;
	
	/**
	 *  user specified starting frame
	 */
	int startingFrame;
	

	/**
	 * @param stGraph parent stGraph
	 * @param pair track ids of parent nodes
	 * @param edge_track presence array for each time point in the stGraph
	 */
	public T1Transition(SpatioTemporalGraph stGraph, int[] pair, Long[] edge_track, int starting_frame) {
		
		this.stGraph = stGraph;
		this.lostEdgeTrack = edge_track;
		this.startingFrame = starting_frame;
		
		assert pair.length == 2: "input pair is not of length 2";
		this.loserNodes = pair;
		
		this.detectionTimePoint = findFirstMissingFrameNo();
		this.oldEdgeSurvivalLength = detectionTimePoint;
		assert detectionTimePoint > 0: "transition could not be identified";
		
		this.transitionLength = computeTransitionLength();
		
		winnerNodes = new int[2];
		Arrays.fill(winnerNodes, -1);
		
	}

	/**
	 * @return the first time point in which the old edge is missing 
	 */
	private int findFirstMissingFrameNo(){
		
		for(int i=0; i<lostEdgeTrack.length; i++)
			if(lostEdgeTrack[i] == null)
				return i + startingFrame;
		
		return -1;
	}
	
	/**
	 * Determine how long the transition was observed
	 * 
	 * @return maximal consecutive transition length
	 */
	private int computeTransitionLength(){
		
		//compute the transition vector, i.e. length of every transition
		int[] transition_vector = new int[lostEdgeTrack.length];
		for(int i=detectionTimePoint - startingFrame; i<lostEdgeTrack.length; i++)
			if(lostEdgeTrack[i] == null){
				transition_vector[i] = transition_vector[i-1] + 1;
				transition_vector[i-1] = 0;
			}
			else
				oldEdgeSurvivalLength++;
		
		//Identify the longest consecutive transition
		int max_length = 0;
		for(int i=0; i<transition_vector.length; i++) {
			if(transition_vector[i] > max_length){
				max_length = transition_vector[i];
				// i:current transition position; max_length:recorded length at i;
				// detection point identifies the beginning of the stretch (+1)
				// startingFrame adjust the time point for a later analysis point chosen by the user
				detectionTimePoint = i - max_length  + 1 + startingFrame;
			}
		}
		
//		//Count the number of Transitions (i.e. minimum number of consecutive losses is 3)
//		Arrays.sort(transition_vector);
//		int array_end = transition_vector.length - 1;
//		for(int i=array_end; i>=0; i--){
//			if(transition_vector[i] < 1){
//				System.out.printf("Found %d permanent track/s out of %d persistent change/s: %s\n",
//						array_end - i, 
//						transition_length,
//						Arrays.toString(
//								Arrays.copyOfRange(transition_vector, i+1, array_end+1)));
//				break;
//			}
//		}
		
		//review which transition is given as output
		return max_length;
	}
	
	
	@Override
	public String toString(){
			return String.format("[%d + %d, %d - %d] @ %d",
					winnerNodes[0],winnerNodes[1],
					loserNodes[0],loserNodes[1],
					detectionTimePoint);
	}
	
	/**
	 * @return tracking id's of loser cells
	 */
	public int[] getLoserNodes(){
		return loserNodes;
	}
	
	/**
	 * @return true if the winner cells have been identified
	 */
	public boolean hasWinners(){
		for(int winner: winnerNodes)
			if(winner == -1)
				return false;

		return true;
	}
	
	/**
	 * @return tracking id's of winning cells
	 */
	public int[] getWinnerNodes(){
		return winnerNodes;
	}
	
	/**
	 * @return frame in which the longest consecutive detection begins
	 */
	public int getDetectionTime(){
		return detectionTimePoint;
	}

	/**
	 * Return how many time points
	 * contain the old edge.
	 * 
	 * @return the amount of frames in which the old edge is visible
	 */
	public int getOldEdgeSurvivalLength() {
		return oldEdgeSurvivalLength;
	}
	
	
	/**
	 * @return true if the transition occurs on the border
	 */
	public boolean onBoundary(){
		
		int previous_frame_no = detectionTimePoint - 1;
		
		FrameGraph previous_frame = stGraph.getFrame(previous_frame_no);
		
		// get the actual tracking code from the array
		int[] loserNodes = Edge.getCodePair(lostEdgeTrack[previous_frame_no - startingFrame]);
		
		assert previous_frame.hasTrackID(loserNodes[0]): "Looser node not found in previous frame";
		assert previous_frame.hasTrackID(loserNodes[1]): "Looser node not found in previous frame";
		
		Node l1 = previous_frame.getNode(loserNodes[0]);
		Node l2 = previous_frame.getNode(loserNodes[1]);
		
		if(l1.onBoundary() || l2.onBoundary())
			return true;
		else
			return false;
	}
	
	/**
	 * Identification of the winner cells
	 * 
	 * @param cell_tiles
	 */
	public void findSideGain(HashMap<Node, PolygonalCellTile> cell_tiles) {
		
		int previous_frame_no = detectionTimePoint - 1;
		FrameGraph previous_frame = stGraph.getFrame(previous_frame_no);
		
		// get the actual tracking code from the array
		int[] loserNodes = Edge.getCodePair(lostEdgeTrack[previous_frame_no - startingFrame]);
		
		assert previous_frame.hasTrackID(loserNodes[0]): "Looser node not found in previous frame";
		assert previous_frame.hasTrackID(loserNodes[1]): "Looser node not found in previous frame";
		
		Node l1 = previous_frame.getNode(loserNodes[0]);
		Node l2 = previous_frame.getNode(loserNodes[1]);
				
		//TODO: substitute with intersection?
//		assert previous_frame.containsEdge(l1, l2): "Input edge is missing in previous frame";
//		Edge lost_edge = previous_frame.getEdge(l1, l2);
//		
//		Geometry lost_edge_geometry = 
//		if(lost_edge.hasGeometry()){
//			
//		}
		Geometry lost_edge_geometry = cell_tiles.get(l1).getTileEdge(l2);
		assert lost_edge_geometry != null: String.format(
				"No edge geometry found @ %d for %s", previous_frame.getFrameNo(), this.toString());
		
		ArrayList<Integer> side_gain_nodes = new ArrayList<Integer>();
		
		for(Node n: l1.getNeighbors())
			if(lost_edge_geometry.intersects(n.getGeometry()))
				if(!n.equals(l2))
					side_gain_nodes.add(n.getTrackID());
		
		assert side_gain_nodes.size() == 2:
			String.format("Winner nodes are more than expected %s",side_gain_nodes.toString());
		
		if(side_gain_nodes.size() == 1){
			System.out.printf("Problems with winner node %d in frame %d, Loosers (%d,%d) ",
					side_gain_nodes.get(0),
					detectionTimePoint,
					loserNodes[0],
					loserNodes[1]
					);
		}
		
		winnerNodes[0] = side_gain_nodes.get(0);
		winnerNodes[1] = side_gain_nodes.get(1);
		
//		FrameGraph detection_frame = stGraph.getFrame(detection_time_point);
//		
//		assert detection_frame.hasTrackID(winner_nodes[0]): "Winner node not found in detection frame";
//		assert detection_frame.hasTrackID(winner_nodes[1]): "Winner node not found in detection frame";
//		
//		Node w1 = detection_frame.getNode(winner_nodes[0]);
//		Node w2 = detection_frame.getNode(winner_nodes[1]);
//		
//		assert detection_frame.containsEdge(w1, w2): "No winner edge found in detection frame";
		
	}

	/**
	 * @return the number of frames in which the new edge / junction is visible
	 */
	public int length() {
		return transitionLength;
	}
}
