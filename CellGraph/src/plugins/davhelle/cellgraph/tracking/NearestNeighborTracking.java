package plugins.davhelle.cellgraph.tracking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.ComparableNode;
import plugins.davhelle.cellgraph.nodes.Node;

/**
 * Nearest Neighbor tracking implements a tracking strategy
 * that associates nodes by cell center correspondence
 * minimization. Moreover when multiple frames are linked
 * i.e. linkrange > 1. A stable marriage solving algorithm
 * is used to identify the perfect matching between the
 * candidates and the node.
 * 
 *  stable-marriage-problem algorithm (GaleŠShapley) source:
 *  http://en.wikipedia.org/wiki/Stable_marriage_problem
 * 
 * @author Davide Heller
 *
 */
public class NearestNeighborTracking extends TrackingAlgorithm{
	
	private int linkrange;

	public NearestNeighborTracking(SpatioTemporalGraph spatioTemporalGraph, int linkrange) {
		super(spatioTemporalGraph);
		this.linkrange = linkrange;
	}
	
	@Override
	public void track(){
		
		/* 
		 * Given the mutually unique relationship between the first frame
		 * and the current frame. All not assigned nodes can be 
		 * classified as either:
		 * + lost or delaminated (first frame only) 
		 * + segmentation errors
		 * + divisions (current frame only).
		 * 
		 * First node candidates are computed at each time point to 
		 * solve the marriage problem for the current frame.
		*/
		
		ArrayList<Node> lost_previous = new ArrayList<Node>();
		ArrayList<Node> lost_next = new ArrayList<Node>();
		
		//for every frame extract all particles
		for(int time_point=0;time_point<stGraph.size(); time_point++){
			System.out.println("Linking frame "+time_point);
			
			//link only from the second time point on
			if(time_point > 0){

				//launch linking
				Map<String, Stack<Node>> unmarried = linkTimePoint(time_point);
				
				//obtain separate unmarried stacks
				Stack<Node> unmarried_brides = unmarried.get("brides");
				Stack<Node> unmarried_grooms = unmarried.get("grooms");
				
				//analyze unmarried brides, i.e. current nodes without previously linked node
				//System.out.println(" uB:"+ unmarried_brides.size());
				while(!unmarried_brides.empty()){
					Node lost = unmarried_brides.pop();

					//Make a rescue attempt on lost node
					//i.e. determine whether a division or
					//major image shift could have happened.
					Node rescued = rescueCandidate(lost);
					
					if(rescued != null){
						//check whether linking missed candidate due to excessive movement
						//TODO add conditional for division case, mother node should
						//not be further searched after division happended..
						Node lastCorrespondence = getMostRecentCorrespondence(time_point, rescued);
						
						if(unmarried_grooms.contains(rescued)){
							updateCorrespondence(lost, lastCorrespondence);
							unmarried_grooms.remove(rescued);
							
							//visual feedback
							System.out.println("Rescue of "+rescued.getTrackID());
							lost_previous.add(lost);
						}
						else{
							//check whether lost node is result of a division process
							//by looking if the center was inside the rescue node at 
							//the previous time point
							//TODO decide whether to add .buffer(6).
							if(lastCorrespondence.getGeometry().contains(lost.getCentroid())){
								
								//initialize division assumption
								Node mother = rescued;
								Node brother = null;
								boolean has_brother = false;
								
								
								//get brother cell by checking neighbors
								for(Node neighbor: lost.getNeighbors())
									if(neighbor.getFirst() == rescued 
									&& lastCorrespondence.getGeometry().contains(neighbor.getCentroid())){
										has_brother = true;
										brother = neighbor;
									}
								
								if(has_brother){
									//initialize new cells
									lost.setFirst(lost);
									lost.setTrackID(tracking_id++);
									
									brother.setFirst(brother);
									brother.setTrackID(tracking_id++);
									
									//Report division to user
									System.out.println(
											"Division:"+mother.getTrackID()+
											"->("+lost.getTrackID()+","+brother.getTrackID()+")");
								}
								else{
									lost_previous.add(lost);
									System.out.println("@User: failed division - "+rescued.getTrackID());
								}
								
								//TODO create division object
							}
							else{
								lost_previous.add(lost);
								System.out.println("@User: outside previous geometry - "+rescued.getTrackID());
							}
						}	
					}				
					else{
						lost_previous.add(lost);
						System.out.println("@User: no rescue candidate");
					}
				}
				
				//analyze unmarried grooms, i.e. first nodes not been associated to current frame
				//System.out.println(" uG:"+ unmarried_grooms.size());
				while(!unmarried_grooms.empty())
					lost_next.add(getMostRecentCorrespondence(time_point, unmarried_grooms.pop()));

			}
			
			//Compute candidates for successive nodes in [linkrange] time frames (one and only assignment x node x frame)
			for(Node current: stGraph.getFrame(time_point).vertexSet())
				for(int i=1; i <= linkrange && time_point + i < stGraph.size(); i++)
					for(Node next: stGraph.getFrame(time_point + i).vertexSet())
						if(next.getGeometry().contains(current.getCentroid()))
							next.addParentCandidate(current);

		}
		
		
		//Update loss information (done now since otherwise the 
		//information is not propagated correctly while tracking)
		//add loss information
		//-2 could not be associated in current frame
		for(Node lost: lost_previous)
			lost.setTrackID(-2);
		
		//-3 will be lost in next frame 
		for(Node lost: lost_next)
			if(lost_previous.contains(lost))
				lost.setTrackID(-4);
			else
				lost.setTrackID(-3);
		
	}

	/**
	 * Linking algorithm based on the stable marriage problem. 
	 * The nodes in the current frame are addressed as "brides"
	 * while the brooms are the candidates from the first frame (expept for divisions).
	 * 
	 * @param time_point of frame to be linked
	 * @return returns 2 Stacks containing the unlinked nodes, accessible trough a map interface ("brides", "grooms")
	 */
	private Map<String, Stack<Node>> linkTimePoint(int time_point) {
		
		//Build two maps to store the evaluated candidates (evaluation <> distance from center)
		Map<Node, List<ComparableNode>> grooms = new HashMap<Node, List<ComparableNode>>();
		Map<Node, List<ComparableNode>> brides = new HashMap<Node, List<ComparableNode>>();;

		//Evaluate the distances of the candidates of each node in current time frame
		evaluateCandidates(grooms, brides, time_point);

		//Order the evaluated candidates in ascending mannor (smallest distances first) 
		orderCandidates(grooms);
		orderCandidates(brides);

		//Initialize data structures
		Map<Node, Node> marriage = new HashMap<Node,Node>();

		Stack<Node> unmarried_grooms = new Stack<Node>();
		Stack<Node> unmarried_brides = new Stack<Node>();
		Stack<Node>	nochoice_grooms = new Stack<Node>();

		//Fill candidates
		unmarried_grooms.addAll(grooms.keySet());
		unmarried_brides.addAll(brides.keySet());

		//Stable marriage problem (GaleŠShapley algorithm)
		while(!unmarried_grooms.empty()){
			//take groom candidate from stack and try to assign
			Node groom = unmarried_grooms.pop();

			//get preference list of groom 
			Iterator<ComparableNode> bride_it = grooms.get(groom).iterator();
			boolean married = false;

			//loop util groom has preferences and is not married
			while(bride_it.hasNext() && !married){

				//get bride candidate and mark her as visited
				Node bride = bride_it.next().getNode();
				bride_it.remove();

				//check if wanted bride is married at all
				if(!marriage.containsKey(bride)){
					unmarried_brides.remove(bride);

					marriage.put(bride, groom);
					married = true;
				}

				//if already married see if current groom is better fit
				else{

					Node old_groom = marriage.get(bride);
					Iterator<ComparableNode> grooms_it = brides.get(bride).iterator();

					//cycle preferences (ascending order, best first)
					while(grooms_it.hasNext()){
						Node preffered_groom = grooms_it.next().getNode();

						//current husband has better rating
						if(preffered_groom == old_groom)
							break;

						//new husband has better rating!
						if(preffered_groom == groom){
							unmarried_grooms.push(old_groom);

							marriage.put(bride, groom);
							married = true;
							break;
						}
					}
				}
			}

			//if groom has no more bride candidates eliminate from list
			if(!marriage.containsValue(groom) && grooms.get(groom).isEmpty())
				nochoice_grooms.push(groom);

		}

		//finally update node correspondences
		for(Node bride: marriage.keySet()){
			Node groom = marriage.get(bride);
			updateCorrespondence(bride, getMostRecentCorrespondence(bride, groom));
		}
		
		
		Map<String, Stack<Node>> unmarried = new HashMap<String, Stack<Node>>();
		
		unmarried.put("brides", unmarried_brides);
		unmarried.put("grooms", nochoice_grooms);
		
		return unmarried;
	}

	/**
	 * Method to order the candidates based on the distance computed
	 * by the evaluation method. Given the ComparableNode object the
	 * inhouse Arrays.sort method can be used.
	 * 
	 * @param node_map Map with node's list to be ordered
	 */
	private void orderCandidates(
			Map<Node, List<ComparableNode>> node_map) {

		for(Node first: node_map.keySet()){
			
			//retrieve
			List<ComparableNode> candidate_list = node_map.get(first);
			int candidate_no = candidate_list.size();
			
			//ascending sort
			ComparableNode[] candidate_array = candidate_list.toArray(new ComparableNode[candidate_no]);
			Arrays.sort(candidate_array);
//			System.out.print(first.getTrackID()+":"+Arrays.toString(candidate_array));
		
			
			//List conversion
			candidate_list = new ArrayList<ComparableNode>(Arrays.asList(candidate_array));
			
			//update (requires change from immutable java.util.Arrays$Arraylist to mutable ArrayList object)
			node_map.put(first, candidate_list);

		}
		
//		System.out.println();

	}

	/**
	 * Evaluate all node's candidate of a particular frame. Evaluation
	 * is intended as quantification of the cell center distance between 
	 * a candidate node and the node being considered.
	 * 
	 * Two Map are filled which represent the two classes which are to be matched. 
	 * Since with a give linkrange we connect multiple frames to a particular
	 * frame in the future. Distance are averaged if candidate node share the
	 * same ancestor (first) node.
	 * 
	 * @param first_map correspondence from first to current frame
	 * @param current_map correspondence from current to first frame
	 * @param time_point time point of the current frame being considered
	 */
	private void evaluateCandidates(Map<Node, List<ComparableNode>> first_map,
			Map<Node, List<ComparableNode>> current_map, int time_point) {
		
		//visit all nodes of the current frame
		for(Node current: stGraph.getFrame(time_point).vertexSet()){
			
			//initialize reference towards which distances are computed
			Point current_cell_center = current.getCentroid();
			
			//given ancestor candidates compute mean distances
			//based on individual nodes linking to the same first() node.
			
			List<Node> candidates = current.getParentCandidates();
			
			//if no candidates are given add it as "lost bride"
			if(candidates.size() == 0){
				if(frame_0_union.contains(current_cell_center))
					current_map.put(current, new ArrayList<ComparableNode>());
			}
			else
				while(candidates.size() > 0){

					Iterator<Node> candidate_it = candidates.iterator();

					Node voted = candidate_it.next();
					Node first = voted.getFirst();
					candidate_it.remove();
					
					Point voted_centroid = voted.getCentroid();

					//Cell could be either new (division/seg.error), 
					//thus not associated to any first node
					if(first == null){
						if(frame_0_union.contains(voted_centroid))
							current_map.put(current, new ArrayList<ComparableNode>());
						continue;
					}

//					// or average distance criteria
//					int count = 1;
//					double sum = DistanceOp.distance(
//							voted_centroid,
//							current_cell_center);
//
//					while(candidate_it.hasNext()){
//						voted = candidate_it.next();
//						if( voted.getFirst() == first){
//							candidate_it.remove();
//							sum +=  DistanceOp.distance(
//									voted_centroid,
//									current_cell_center);
//							count++;
//						}
//					}
//
//					double avg = sum / count;
					
					
					//min search criteria
					double min = DistanceOp.distance(
							voted_centroid,
							current_cell_center);

					while(candidate_it.hasNext()){
						voted = candidate_it.next();
						if( voted.getFirst() == first){
							candidate_it.remove();
							double candidate_dist = DistanceOp.distance(
									voted_centroid,
									current_cell_center);
							if(min > candidate_dist)
								min = candidate_dist;

						}
					}
					
					double avg = min;

					//assign candidate to both maps with the respective distance

					//first -> current
					if(!first_map.containsKey(first))
						first_map.put(first, new ArrayList<ComparableNode>());

					ComparableNode candidate_distance = new ComparableNode(current,avg);
					first_map.get(first).add(candidate_distance);

					//current -> first
					if(!current_map.containsKey(current))
						current_map.put(current, new ArrayList<ComparableNode>());

					current_map.get(current).add(new ComparableNode(first, avg));

					//the two maps will be latter matched by solving an abstracted
					//stable marriage problem
				}
		}	
	}
}

