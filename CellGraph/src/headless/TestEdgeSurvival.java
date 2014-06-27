package headless;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.misc.PolygonalCellTile;
import plugins.davhelle.cellgraph.nodes.Edge;
import plugins.davhelle.cellgraph.nodes.Node;

public class TestEdgeSurvival {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		//Input files
		File test_file = new File("/Users/davide/tmp/T1/test/test_t0000.tif");
		int no_of_test_files = 10;

		SpatioTemporalGraph stGraph = T1Transitions.createSpatioTemporalGraph(test_file,
				no_of_test_files);

		//TODO: What about the polygonal tile storing the geometries of the edges?
		HashMap<Node, PolygonalCellTile> cell_tiles = T1Transitions.createPolygonalTiles(stGraph);
		
		//for every considered edge
		//neighbors must be alive, divide or be eliminated.
		
		HashMap<Long,Integer> tracked_edges = new HashMap<Long,Integer>();
		
		for(int i=0; i<stGraph.size(); i++){
			
			FrameGraph frame_i = stGraph.getFrame(i);
			int no_of_tracked_edges = 0;
			for(Edge e: frame_i.edgeSet()){
				if(e.isTracked(frame_i)){
					
					long edge_track_code = e.getPairCode(frame_i);
				
					if(i==0)
						tracked_edges.put(edge_track_code,0);
					
					if(tracked_edges.containsKey(edge_track_code)){
						int old = tracked_edges.get(edge_track_code);
						tracked_edges.put(edge_track_code, old + 1);
						no_of_tracked_edges++;
					}
				}
			}
			System.out.printf("Sample contains %d/%d trackable edges in frame %d\n",
					no_of_tracked_edges,frame_i.edgeSet().size(),i);
		}
		
		for(long track_code:tracked_edges.keySet()){
			int[] pair = Edge.getCodePair(track_code);
			System.out.printf("%s(%d)\n",Arrays.toString(pair),tracked_edges.get(track_code));
		}
		
		//if edge not present in i+1: increase temporal edge otherwise skip or discard if s/t nodes absent
		//final divide edge according to length to measure stability
		

	}


}
