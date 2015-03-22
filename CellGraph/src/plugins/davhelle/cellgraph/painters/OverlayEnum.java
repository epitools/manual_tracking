package plugins.davhelle.cellgraph.painters;

/**
 * Enumeration of available overlays in the CellOverlay-Plugin
 * 
 * @author Davide Heller
 *
 */
public enum OverlayEnum{
	
	TEST("Test Overlay"),
	CELL_OVERLAY("Simple Overlay to show cells and their outlines in a color of choice"),
	CELL_AREA("Overlay to color cells according to their area size in a gradient fashion"),
	SEGMENTATION_BORDER("Overlay to show where the border of the segmentation was identified"), 
	
	VORONOI_DIAGRAM("Overlay displays the voronoi diagram computed from the cell centroids"), 
	POLYGON_CLASS(
			"Displays the number of neighbors each cell has with color code or number; "+
	"The [save] option stores a csv file with 1 line of pc-counts for each frame"
	),
	//POLYGON_TILE("Overlay that simplifies the geometry of each cell to have straight edges"),
	GRAPH_VIEW("Shows the connectivity (neighbors) of each cell"),
	
	CELL_TRACKING("Overlay to review the tracking in case it has been eliminated or to highlight different aspects"),
	//ALWAYS_TRACKED_CELLS("Highlights only the cells that have been continuously tracked throughout the time lapse"),
	DIVISIONS_AND_ELIMINATIONS(DivisionOverlay.DESCRIPTION),
	CORRECTION_HINTS(CorrectionOverlay.DESCRIPTION),
	
	GRAPHML_EXPORT("Exports the currently loaded graph into a GraphML file"),
	//WRITE_OUT_DDN("Statistics output"),
	CELL_COLOR_TAG(CellMarkerOverlay.DESCRIPTION),
	//SAVE_SKELETONS("Saves the imported skeletons with modifications (e.g. small cell removal/border removal) as separate set"),
	
	T1_TRANSITIONS("Computes and displays the T1 transitions present in the time lapse [time consuming!]"), 
	EDGE_STABILITY(EdgeStabilityOverlay.DESCRIPTION),
	EDGE_INTENSITY("Transforms the edge geometries into ROIs and displays the underlying intensity of the image [time consuming!]"),
	//NEIGHBOR_STABILITY("An overlay to display the stability of each neighbor (graph based)"),  
	
	ELLIPSE_FIT("Fits an ellipse to each cell geometry and displays the longest axis"),
	ELLIPSE_FIT_WRT_POINT_ROI(EllipseFitColorOverlay.DESCRIPTION),
	ELONGATION_RATIO("Color codes the cell according to their elongation ratio and "+
	"writes the elongation factor within every cell"), // could add csv option here
	PDF_SCREENSHOT("Generates a screenshot in PDF format"),
	DIVSION_ORIENTATION(DivisionOrientationOverlay.DESCRIPTION);
	
	private String description;
	private OverlayEnum(String description){this.description = description;}
	public String getDescription(){return description;}
}
