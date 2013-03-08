package plugins.davhelle.cellgraph;

import java.awt.Shape;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 * NodeType represents the vertex object for the TissueGraph.
 * Among various implementations examples can be:
 * a Cell type representing the cell relation in the tissue
 * or a Corner type representing the vertex relation.
 * 
 * By default it is assumed that there is a JTS Geometry field.
 * 
 * @author Davide Heller
 *
 */
public interface NodeType {
	
	
	/**
	 * Given a geometrical representation of the node(JTS)
	 * the method returns the centroid of the latter geometry.
	 * In case of a Point, the latter will be returned.
	 * 
	 * @return centroid of the geometry
	 */
	public Point getCentroid();
	
	/**
	 * @return JTS geometry representing the NodeType
	 */
	public Geometry getGeometry();

	/**
	 * Abstract feature of a NodeType. For example for 
	 * a polygonal cell representation this might include
	 * the voroni tesselation.  
	 * 
	 * @return a defined property of the implementation 
	 */
	public Object getProperty();
	
	/**
	 * Given that the HyperGraph structure has been tracked
	 * in time the method returns the idx assigned to the node.
	 * 
	 * @return constant tracking index for the cell or default -1
	 */
	public int getTrackID();
	
	/**
	 * Assign a tracking id to the node after tracking
	 * in time.
	 * 
	 * @param tracking_id
	 */
	public void setTrackID(int tracking_id);
	
	/**
	 * Transforms the geometrical representation of the node
	 * into an awt.shape.
	 * 
	 * @return java.awt.Shape of the node's geometry(JTS)
	 */
	public Shape toShape();
}
