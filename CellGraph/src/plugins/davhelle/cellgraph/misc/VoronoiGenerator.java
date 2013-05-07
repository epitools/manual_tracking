/*=========================================================================
 *
 *  Copyright Basler Group, Institute of Molecular Life Sciences, UZH
 *
 *=========================================================================*/
package plugins.davhelle.cellgraph.misc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Node;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.triangulate.VoronoiDiagramBuilder;

public class VoronoiGenerator {

	private Map<Node, Geometry> nodeVoronoiMap;
	private Map<Node, Double> areaDifferenceMap;
	
	public VoronoiGenerator(SpatioTemporalGraph stGraph) {
		
		this.nodeVoronoiMap = new HashMap<Node,Geometry>();
		this.areaDifferenceMap = new HashMap<Node, Double>();
		
		//for every frame
		for(int i=0; i<stGraph.size(); i++){
			
			//Set up JTS Voronoi diagram builder
			VoronoiDiagramBuilder vdb = new VoronoiDiagramBuilder();
			vdb.setClipEnvelope(new Envelope(0, 512, 0, 512));
			Collection<Coordinate> coords = new ArrayList<Coordinate>();

			for(Node cell: stGraph.getFrame(i).vertexSet())
				coords.add(cell.getCentroid().getCoordinate());
			
			//set voronoi diagram sites with cell center coordinates
			vdb.setSites(coords);
			
			Geometry voronoiDiagram = vdb.getDiagram(new GeometryFactory());
			
			//find mapping between voronoi_polygons and cell_centers
			for(int j=0; j<voronoiDiagram.getNumGeometries(); j++){
					Geometry voroniPolygon = voronoiDiagram.getGeometryN(j);
					for(Node cell: stGraph.getFrame(i).vertexSet()){
						if(voroniPolygon.contains(cell.getCentroid())){
							nodeVoronoiMap.put(cell, voroniPolygon);		
							
							//Compute area differnce between polygonal cell and area
							double cell_area = cell.getGeometry().getArea();
							double voronoi_area = voroniPolygon.getArea();
							double area_difference = cell_area - voronoi_area;
							
							areaDifferenceMap.put(cell, area_difference);
						}
						
					}
			}
		}
	}
	
	public Map<Node,Geometry> getNodeVoroniMapping(){
		return nodeVoronoiMap;
	}
	
	public Map<Node,Double> getAreaDifference(){
		return areaDifferenceMap;

	}

}
