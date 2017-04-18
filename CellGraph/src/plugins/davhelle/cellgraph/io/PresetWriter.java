package plugins.davhelle.cellgraph.io;

/**
 * Export class to save a static version of the currently
 * displayed graph, that can be loaded via the TestPlugin.
 * 
 * All methods are currently included in CellExport, see
 * plugins.davhelle.cellgraph.CellExport.savePreset(Sequence, SpatioTemporalGraph)
 * for more details
 * 
 * @author Davide Heller
 *
 */
public class PresetWriter {
	
	public static final String DESCRIPTION = 
			"Exports current dataset as Preset such that it can be <br/>" +
			"loaded as test set with the TestLoader plugin.<br/><br/>" +
			"The output folder includes the following:<br/>" +
			"- current sequence used to visualize -> Skeletons.tif<br/>" +
			"- current skeletons used -> WKT skeletons <br/>" +
			"- current tracking -> CSV tracking <br/>";
	
}
