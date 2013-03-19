package plugins.davhelle.cellgraph.misc;

import java.util.HashMap;
import java.util.Vector;

import com.vividsolutions.jts.geom.Coordinate;

import mosaic.core.detection.MyFrame;
import mosaic.core.detection.Particle;
import mosaic.core.particleLinking.ParticleLinker;
import plugins.davhelle.cellgraph.graphs.DevelopmentType;
import plugins.davhelle.cellgraph.graphs.TissueGraph;
import plugins.davhelle.cellgraph.nodes.NodeType;

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
	private HashMap<Particle,NodeType> particle2NodeMap;
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
		this.linkrange = 5; //TODO tune
		this.displacement = 10; //TODO tune
		this.frames = new MyFrame[frames_number];
		this.particle2NodeMap = new HashMap<Particle,NodeType>();
		
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
		
		for(int i=0;i<frames_number; i++){
			//get graph
			TissueGraph graph_i = stGraph.getFrame(i);
			Vector<Particle> particles = new Vector<Particle>();
			int particle_number = graph_i.size();
			
			//convert graph nodes 
			for(NodeType n: graph_i.vertexSet()){
				//JTS coordinate
				Coordinate centroid = 
						n.getCentroid().getCoordinate();
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
		for(NodeType n: stGraph.getFrame(0).vertexSet())
			n.setTrackID(tracking_id++);
		//alternative	n.setTrackID(n.hashCode());	

		//for every frame extract all particles
		for(int i=0;i<frames_number-1; i++){
			Vector<Particle> particles = 
					frames[i].getParticles();
			
			//for every particle update the corresponding NodeType
			for(Particle p: particles){
				NodeType n = particle2NodeMap.get(p);
				
				//TODO covers ONLY simple case linkage==1!!!
				
				int pNext_idx = 0;
				int j=0;
				for(; j<p.next.length; j++){
					pNext_idx = p.next[j];
					if(pNext_idx != -1)
						break;
				}
				
				int pNextFrame = i + j + 1;
				
				//Only update is valid id inserted, otherwise leave TrackID=-1
				if(pNext_idx != -1 && pNextFrame < stGraph.size() && 
						pNext_idx < frames[pNextFrame].getParticles().size()){
					
					Particle pNext = frames[pNextFrame].getParticles().get(pNext_idx);
					NodeType nNext = particle2NodeMap.get(pNext);
					nNext.setTrackID(n.getTrackID());
					n.setNext(nNext);
				}
			}			
		}
		
		this.stGraph.setTracking(true);
	}

}