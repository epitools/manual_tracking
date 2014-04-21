/*=========================================================================
 *
 *  Copyright Basler Group, Institute of Molecular Life Sciences, UZH
 *
 *=========================================================================*/
/**
 * 
 */
package plugins.davhelle.cellgraph.graphs;

import java.util.ArrayList;

/**
 * @author Davide Heller
 *
 */
public class TissueEvolution implements SpatioTemporalGraph {

	private ArrayList<FrameGraph> frames;
	private boolean has_tracking;
	private boolean has_voronoi;
	
	/**
	 * 
	 */
	public TissueEvolution(int time_points) {
		// TODO Auto-generated constructor stub
		this.has_tracking = false;
		this.has_voronoi = false;
		this.frames = new ArrayList<FrameGraph>(time_points);
	}
	
	public TissueEvolution(){
		this(0);
	}

	/* (non-Javadoc)
	 * @see plugins.davhelle.cellgraph.graphs.DevelopmentType#getFrame(int)
	 */
	@Override
	public FrameGraph getFrame(int frame_no) {
		return frames.get(frame_no);
	}

	/* (non-Javadoc)
	 * @see plugins.davhelle.cellgraph.graphs.DevelopmentType#setFrame(plugins.davhelle.cellgraph.graphs.TissueGraph, int)
	 */
	@Override
	public void setFrame(FrameGraph graph, int frame_no) {
		if(frames.size() > frame_no)
			frames.set(frame_no, graph);
		else
			frames.add(graph);
	}
	
	public void addFrame(FrameGraph graph){
		frames.add(graph);
	}

	/* (non-Javadoc)
	 * @see plugins.davhelle.cellgraph.graphs.DevelopmentType#size()
	 */
	@Override
	public int size() {
		return frames.size();
	}

	/* (non-Javadoc)
	 * @see plugins.davhelle.cellgraph.graphs.DevelopmentType#hasTracking()
	 */
	@Override
	public boolean hasTracking() {
		return has_tracking;
	}

	/* (non-Javadoc)
	 * @see plugins.davhelle.cellgraph.graphs.DevelopmentType#hasVoronoi()
	 */
	@Override
	public boolean hasVoronoi() {
		return has_voronoi;
	}

	@Override
	public void setTracking(boolean new_state) {
		this.has_tracking = new_state;
	}

	@Override
	public void setVoronoi(boolean new_state){
		this.has_voronoi = new_state;
	}

}
