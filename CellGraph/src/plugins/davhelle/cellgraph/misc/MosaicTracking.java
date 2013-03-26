package plugins.davhelle.cellgraph.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;

import mosaic.core.detection.MyFrame;
import mosaic.core.detection.Particle;
import mosaic.core.particleLinking.ParticleLinker;
import plugins.davhelle.cellgraph.graphs.DevelopmentType;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.nodes.Node;

/**
 * Track centroids of node types represented
 * in the DevelopmentType spatiotemporal graph
 * 
 * 
 * @author Davide Heller
 *
 */
public class MosaicTracking {

	private DevelopmentType stGraph;
	private MyFrame[] frames;
	private HashMap<Particle,Node> particle2NodeMap;
	private int frames_number;
	private int linkrange;
	private float displacement;
	
	/**
	 * Setup tracking structures required for ParticleLinker class
	 * 
	 * @param spatioTemporalGraph Spatio temporal graph to be tracked
	 */
	public MosaicTracking(DevelopmentType spatioTemporalGraph) {
		this.stGraph = spatioTemporalGraph;
		this.frames_number = spatioTemporalGraph.size();
		this.linkrange = 10; //TODO tune
		this.displacement = 10; //TODO tune
		this.frames = new MyFrame[frames_number];
		this.particle2NodeMap = new HashMap<Particle,Node>();
		
		//convert stGraph into MyFrame array
		particleConversion();
		
	}
	
	/**
	 * Run tracking and update tracking IDs of graph nodes (NodeType)
	 */
	public void track(){
		//create Linker
		ParticleLinker linker = new ParticleLinker();
		
		System.out.println("Initialized ParticleLinker, start tracking..");
		//execute
		linker.linkParticles(frames, frames_number, linkrange, displacement);
		System.out.println("...completed tracking! Linking particles to nodes");

		//update graph structure
		updateGraph();
		
		System.out.println("...done!");
	}
	
	/**
	 * Internal method to convert all NodeTypes in each TissueGraph
	 * into Particle objects collected into MyFrame objects. Latter
	 * is required for ParticleLinker class.
	 */
	private void particleConversion(){
		
		//extract single frame and convert to MyFrame
		//set of Particles
		Geometry frame_0_union = null;
		
		for(int i=0;i<frames_number; i++){
			//get graph
			FrameGraph graph_i = stGraph.getFrame(i);
			Vector<Particle> particles = new Vector<Particle>();
			int particle_number = graph_i.size();
			
			//Build the union of frame 0 to discard 
			if(i==0){

				Geometry[] output = new Geometry[graph_i.size()];
				Iterator<Node> node_it = graph_i.iterator();
				for(int j=0; j<graph_i.size(); j++){
					output[j] = node_it.next().getGeometry();
				}		

				//Create union of all polygons
				GeometryCollection polygonCollection = new GeometryCollection(output, new GeometryFactory());
				//TODO check if better to add a little buffer
				frame_0_union = polygonCollection.buffer(0);
			}
			
			
			//convert graph nodes 
			for(Node n: graph_i.vertexSet()){
				//JTS coordinate
				Geometry node_centroid = n.getCentroid();

				//only add particles of nodes within the frame_0 boundary
				if(frame_0_union.contains(node_centroid)){
					//mark node as tracked but not necessarily resolved
					n.setTrackID(-2);
					
					Coordinate centroid = node_centroid.getCoordinate();
					//MOSAIC particle
					//centroid.z not available TODO check polygonizer
					//if used tracker won't work
					Particle p = 
							new Particle(
									(float)centroid.x,
									(float)centroid.y,
									(float)0,
									i,
									linkrange);
					//update particle vector
					particles.add(p);

					particle2NodeMap.put(p, n);
				}
			}
			
			//define MOSAIC myFrame
			frames[i] = new MyFrame(particles, i, linkrange);
			
		}
		
	}
	
	/**
	 * internal method to update all tracking indices
	 * of NodeTypes by using the tracking information
	 * stored in MyFrames Particles. Correspondence
	 * is achieved through particle2NodeMap. 
	 */
	private void updateGraph(){
		//for simple case linkage=1
		
		//first set trackID of first graph (reference)
		int tracking_id = 0;
		for(Node n: stGraph.getFrame(0).vertexSet())
			n.setTrackID(tracking_id++);
		//alternative	n.setTrackID(n.hashCode());	

		//for every frame extract all particles
		for(int i=0;i<frames_number-1; i++){
			Vector<Particle> particles = 
					frames[i].getParticles();
			
			//for every particle update the corresponding NodeType
			for(Particle p: particles){
				Node n = particle2NodeMap.get(p);
				
				//frame of correspondent particle
				int next_frame_idx = i;
				boolean is_linked = false; 
				
				//Update all correspondences in time available
				for(int linked_idx: p.next){
					next_frame_idx++;
					if(linked_idx != -1){
						
						//obtain corresponding particle in future and latter's node
						Particle pNext = frames[next_frame_idx].getParticles().get(linked_idx);
						Node nNext = particle2NodeMap.get(pNext);
						
						//update correspondent particle (will be overwritten multiple times)
						nNext.setTrackID(n.getTrackID());
						nNext.setPrevious(n);
						
						//update current particle with the closest correspondence
						if(!is_linked){
							n.setNext(nNext);
							is_linked = true;
						}
					}
				}
			}			
		}
		
		this.stGraph.setTracking(true);
	}

}
