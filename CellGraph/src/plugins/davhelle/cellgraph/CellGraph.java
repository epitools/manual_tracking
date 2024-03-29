package plugins.davhelle.cellgraph;

import icy.gui.frame.progress.AnnounceFrame;
import icy.main.Icy;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import icy.swimmingPool.SwimmingObject;
import icy.swimmingPool.SwimmingPool;

import java.awt.Color;
import java.io.File;
import java.util.List;

import plugins.adufour.ezplug.EzGroup;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarDouble;
import plugins.adufour.ezplug.EzVarEnum;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarFloat;
import plugins.adufour.ezplug.EzVarFolder;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.davhelle.cellgraph.graphs.GraphType;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraphGenerator;
import plugins.davhelle.cellgraph.io.CsvTrackReader;
import plugins.davhelle.cellgraph.io.FileNameGenerator;
import plugins.davhelle.cellgraph.io.InputType;
import plugins.davhelle.cellgraph.io.SegmentationProgram;
import plugins.davhelle.cellgraph.misc.BorderCells;
import plugins.davhelle.cellgraph.misc.SmallCellRemover;
import plugins.davhelle.cellgraph.overlays.DisplacementOverlay;
import plugins.davhelle.cellgraph.overlays.PolygonOverlay;
import plugins.davhelle.cellgraph.overlays.TrackIdOverlay;
import plugins.davhelle.cellgraph.overlays.TrackingOverlay;
import plugins.davhelle.cellgraph.tracking.HungarianTracking;
import plugins.davhelle.cellgraph.tracking.StableMarriageTracking;
import plugins.davhelle.cellgraph.tracking.TrackingAlgorithm;
import plugins.davhelle.cellgraph.tracking.TrackingEnum;

/**
 * <b>CellGraph</b> is a plugin for the bioimage analysis tool 
 * <a href="http://icy.bioimageanalysis.org">icy</a><br><br>
 * 
 * The plugin is part of EpiTools, an image analysis toolkit for
 * the epithelial growth dynamics. Find more about it 
 * our <a href="http://tiny.uzh.ch/dm">project homepage</a>.<br><br>
 * 
 * The plugin provides an interface to create {@link SpatioTemporalGraph} objects
 * and export them in icy's swimming pool memory structure for further analysis.
 * To create a spatio-temporal graph input files are required. We support several
 * files of which the most prominent is the skeleton bitmap image.<br><br>
 * 
 * Required libraries:<br>
 * - JTS (Java Topology Suite) for the vtkmesh to polygon transformation
 * 		and to represent the Geometrical objects, version 1.13<br>
 * - JGraphT to represent the graph structure of a single time point, version 0.9<br>
 * - EzPlug by Alexandre Dufour for the GUI interface<br>
 * 
 * @author Davide Heller
 *
 */

public class CellGraph extends EzPlug implements EzStoppable
{
	
	//Ezplug fields 
	EzVarEnum<TrackingEnum>		varTrackingAlgorithm;
	EzVarEnum<InputType>		varInput;
	EzVarEnum<SegmentationProgram> varTool;
	EzVarFile					varFile;
	EzVarBoolean				varDirectInput;
	EzVarSequence				varSequence;
	
	//Input limitation
	EzVarInteger				varMaxZ;
	EzVarInteger				varMaxT;
	EzVarBoolean				varAllT;
	EzVarInteger				varBorderEliminationNo;
	
	//EzPlug options
	EzVarBoolean				varUpdatePainterMode;
	EzVarBoolean				varCutBorder;
	
	//Tracking Parameters
	EzVarBoolean 				varDoTracking;
	EzVarBoolean				varBooleanHighlightMistakesBoolean;
	EzVarBoolean 				varBooleanDrawDisplacement;
	EzVarInteger				varLinkrange;
	EzVarFloat					varDisplacement;
	EzVarDouble					varLambda1;
	EzVarDouble					varLambda2;
	EzVarBoolean				varBooleanCellIDs;
	
	//Remove cells
	EzVarBoolean				varRemoveSmallCells;
	EzVarDouble					varAreaThreshold;
	
	//Load structure into Swimming Pool
	EzVarBoolean				varUseSwimmingPool;
	
	//Load Track from CSV files
	EzVarFolder 				varLoadFile;
	
	//Stop flag for advanced thread handling
	boolean						stopFlag;
	
	//sequence to paint on 
	Sequence sequence;
	private EzVarBoolean varUsePackingAnalyzer;
	
	@Override
	protected void initialize()
	{
		//Ezplug variable initialization
		
		//Main Input GUI: which files to use?
		EzGroup groupInputPrameters = initializeInputGUI();
		
		//Tracking GUI: apply tracking?
		varDoTracking = new EzVarBoolean("    2. SELECT IF TO TRACK CELLS", false);
		varDoTracking.setToolTipText("Choose whether to link frames in time");
		EzGroup groupTracking = initializeTrackingGUI();
		
		//Output GUI: where to visualize
		EzGroup groupVisual = initializeOutputGUI();

		//Make items visible
		super.addEzComponent(groupInputPrameters);
		super.addEzComponent(varDoTracking);
		super.addEzComponent(groupTracking);
		super.addEzComponent(groupVisual);

		//set visibility trigger to TrackingGroup
		varDoTracking.addVisibilityTriggerTo(groupTracking, true);
		
		this.setTimeDisplay(true);
		
	}

	/**
	 * Initializes the input EzGUI handles
	 * 
	 * @return a group containing the initialized input handles
	 */
	private EzGroup initializeInputGUI() {
		//What input is given
		varInput = new EzVarEnum<InputType>(
				"File type",InputType.values(), InputType.SKELETON);
		varInput.setToolTipText("Type of the file that contains the cell outlines");
		
		//Constraints on file, time and space
		varFile = new EzVarFile("First file", "/Users/davide/data/");
		varFile.setToolTipText("For series choose the first time point, e.g. frame001.png");
		
		//varMaxZ = new EzVarInteger("Max z height (0 all)",0,0, 50, 1);
		varMaxT = new EzVarInteger("Time points to load:",1,1,100,1);
		varMaxT.setToolTipText("For t>1: [base]001.[ext] file pattern is required");
		varAllT = new EzVarBoolean("Load all time points", false);
		varAllT.setToolTipText("Reads the time points number from the output sequence");
		
		EzVarBoolean varUseAdvanceOptions = 
				new EzVarBoolean("Show advanced options",false);
		
		//Should the data be directly imported from a particular tool data structure
		varDirectInput = new EzVarBoolean("Known source", true);
		
		//In case yes, what particular program was used
		varTool = new EzVarEnum<SegmentationProgram>(
				"\tSegmentation tool",SegmentationProgram.values(), SegmentationProgram.MatlabLabelOutlines);
		varUsePackingAnalyzer = new EzVarBoolean("PackingAnalyzer files", false);
		varUsePackingAnalyzer.setToolTipText("CellGraph will use [imageName]/handCorrected.png as skeleton");

		//Border cut
		varCutBorder = new EzVarBoolean("Cut one border line",false);
		varCutBorder.setToolTipText("Remove cells on the segmentation border");
		
		//small cell elimination
		varRemoveSmallCells = new EzVarBoolean("Remove very small cells", true);
		varRemoveSmallCells.setToolTipText("Remove cells below the threshold and merge their area");
		varAreaThreshold = new EzVarDouble("\tThreshold area [px]", 10, 0, Double.MAX_VALUE, 0.1);
		varAreaThreshold.setToolTipText("Area below which cells will be removed");
		varRemoveSmallCells.addVisibilityTriggerTo(varAreaThreshold, true);
		
		EzGroup inputTypeGroup = new EzGroup("Optional input parameters",
				//varDirectInput,
				//varTool,
				varUsePackingAnalyzer,
				varCutBorder,
				varRemoveSmallCells,
				varAreaThreshold
				);
		
		EzGroup groupInputPrameters = new EzGroup("1. SELECT INPUT FILES",
				varInput,
				varFile, 
				varMaxT,
				varAllT,
				varUseAdvanceOptions,
				inputTypeGroup
				);
		
		varUseAdvanceOptions.addVisibilityTriggerTo(inputTypeGroup, true);
		varInput.addVisibilityTriggerTo(varDirectInput, InputType.SKELETON);
		varInput.addVisibilityTriggerTo(varTool, InputType.SKELETON);
		
		//These commands should only be available when the inputType is not wkt
		varInput.addVisibilityTriggerTo(varCutBorder,
				InputType.SKELETON,InputType.VTK_MESH);
		
		varDirectInput.addVisibilityTriggerTo(varTool, true);
		
		return groupInputPrameters;
	}

	/**
	 * Initializes the tracking EzGUI handles
	 * 
	 * @return a group containing the initialized tracking handles
	 */
	private EzGroup initializeTrackingGUI() {
		//Track view
		varLinkrange = new EzVarInteger(
				"Propagation Limit [frames]", 5,1,100,1);
		varLinkrange.setToolTipText("Limit the propagation of tracking information from one frame");
		
		varDisplacement = new EzVarFloat(
				"Max. displacement (px)",5,1,20,(float)0.1);
		varBooleanCellIDs = new EzVarBoolean("Write TrackIDs", true);
		varBooleanDrawDisplacement = new EzVarBoolean("Draw displacement", false);
		varBooleanHighlightMistakesBoolean = new EzVarBoolean("Highlight mistakes", true);
		varTrackingAlgorithm = new EzVarEnum<TrackingEnum>("Algorithm",TrackingEnum.values(), TrackingEnum.STABLE_MARRIAGE);
		varBorderEliminationNo = new EzVarInteger("Cut N border lines in 1st frame",1,0,10,1);
		varBorderEliminationNo.setToolTipText("Restrict tracking by removing border cells in the 1st frame");
		
		varLambda1 = new EzVarDouble("Min. Distance weight", 1, 0, 10, 0.1);
		varLambda2 = new EzVarDouble("Overlap Ratio weight", 1, 0, 10, 0.1);
		
		varLoadFile = new EzVarFolder("Select csv location", "");
		varLoadFile.setToolTipText("Choose the folder where the CSV tracking files have been saved");

		EzGroup groupTrackingParameters = new EzGroup("Algorithm parameters",
				varLinkrange,
				//varDisplacement,
				//varLambda1,
				//varLambda2,
				varBorderEliminationNo
				//varBooleanCellIDs,
				//varBooleanHighlightMistakesBoolean,
				//varBooleanDrawDisplacement
				);
		
		EzGroup groupTracking = new EzGroup("SELECT TRACKING ALGORITHM",
				varTrackingAlgorithm,
				groupTrackingParameters,
				varLoadFile
				);
		
		varTrackingAlgorithm.addVisibilityTriggerTo(varLoadFile, TrackingEnum.LOAD_CSV_FILE);
		varTrackingAlgorithm.addVisibilityTriggerTo(groupTrackingParameters, 
				TrackingEnum.STABLE_MARRIAGE,TrackingEnum.HUNGARIAN);
		
		groupTrackingParameters.setVisible(false);
		
		return groupTracking;
	}

	/**
	 * Initializes the output GUI handles
	 * 
	 * @return a group with the initialized output handles
	 */
	private EzGroup initializeOutputGUI(){
		
		//Working image 
		varSequence = new EzVarSequence("Image to overlay");
		varSequence.setToolTipText("The image on which to display the result overlay");
		
		//Usage of temporary ICY-memory (java object swimming pool)
		varUseSwimmingPool = new EzVarBoolean("Use ICY-SwimmingPool", true);
		varUseSwimmingPool.setToolTipText("Make the results available to CellOverlay & co");
		
		//Pre-existent overlays handling
		varUpdatePainterMode = new EzVarBoolean("Remove Previous Overlays", false);
		varUpdatePainterMode.setToolTipText("Remove old overlays from the Image to overlay");
		
		return new EzGroup("3. SELECT DESTINATION",
				varSequence,
				varUseSwimmingPool,
				varUpdatePainterMode);	
	}
	
	@Override
	protected void execute()
	{	
		stopFlag = false;
		
		//check input sequence
		if(icyAssert(varSequence.getValue() != null,
				"Plugin requires active sequence! " +
				"Please open an image on which to display results"))
			return;
		
		sequence = varSequence.getValue();
		
		//Check for user choice regarding time points
		if(varAllT.getValue())
			varMaxT.setValue(sequence.getSizeT());
		
		//Build input file names from user input
		String[] input_file_paths = generateInputPaths();
		
		//Check for input correctness
		if(wrongInputCheck(input_file_paths))
			return;
		
		//Override current overlays if desired	
		if(varUpdatePainterMode.getValue())
			removeAllOverlays();

		//Create spatio temporal graph from mesh files
		SpatioTemporalGraph stGraph = generateSpatioTemporalGraph(input_file_paths);

		//safety check, e.g. in case of user interruption
		if(stGraph.size() != varMaxT.getValue())
			return;

		//Border identification + discard/mark
		applyBorderOptions(stGraph);

		//Small cell handling, executed after border options 
		if(varRemoveSmallCells.getValue())
			new SmallCellRemover(stGraph).removeCellsBelow(varAreaThreshold.getValue());

		//Check for interruptions
		if(stopFlag)
			return;
		
		if(varDoTracking.getValue())
			applyTracking(stGraph);
		else
			sequence.addOverlay(new PolygonOverlay(stGraph,Color.red));

		//Load the created stGraph into ICY's shared memory, i.e. the swimmingPool
		if(varUseSwimmingPool.getValue())
			pushToSwimingPool(stGraph);	
		
		this.getUI().setProgressBarValue(0);
		this.getUI().setProgressBarMessage("Creation Completed!");
	}

	/**
	 * Generates the absolute path for each input file
	 * 
	 * @return an array containing the absolute file path for each input file
	 */
	private String[] generateInputPaths() {
		
		int time_points_no = varMaxT.getValue();
		
		String[] input_file_paths = new String[time_points_no];
		
		File input_file = varFile.getValue(false);
		
		if(input_file == null)
			return null;
		
		if(varUsePackingAnalyzer.getValue())
			varTool.setValue(SegmentationProgram.PackingAnalyzer);
		
		FileNameGenerator file_name_generator = null;

		//For single files not adopting FileNameGenerator rules
		if(time_points_no != 1 || varTool.getValue() == SegmentationProgram.PackingAnalyzer)
			file_name_generator = new FileNameGenerator(
					input_file,
					varInput.getValue(), 
					varDirectInput.getValue(), 
					varTool.getValue());
			
		varFile.setButtonText(input_file.getName());
		
		this.getUI().setProgressBarMessage("Creating Spatial Graphs...");

		for(int i = 0; i< time_points_no; i++){
			
			String abs_path = "no_file_selected";
			
			//For single files not adopting FileNameGenerator rules
			if(time_points_no == 1 && varTool.getValue() != SegmentationProgram.PackingAnalyzer)
				abs_path = input_file.getAbsolutePath();
			else
				abs_path = file_name_generator.getFileName(i);
			
			input_file_paths[i] = abs_path;
		}
		
		return input_file_paths;
	}

	/**
	 * Safety checks for wrong GUI input
	 * 
	 * @param input_file_paths array containing the absolute paths of the input files 
	 * @return true if input is wrong
	 */
	private boolean wrongInputCheck(String[] input_file_paths) {

		//Check input files
		if(icyAssert(input_file_paths != null,
				"Mesh file required to run plugin! Please set mesh file"))
			return true;
		
		if(input_file_paths[0].endsWith(".wkt"))
			varInput.setValue(InputType.WKT);
		
		for(int i=0; i< input_file_paths.length; i++ ){
			
			String abs_path = input_file_paths[i];
			File current_file = new File(abs_path);
			if(icyAssert(current_file.exists(),"Missing skeleton file: " + abs_path))
				return true;
			
			if(varInput.getValue() == InputType.WKT){
				String export_folder = varFile.getValue().getParent();
				File expected_wkt_file = new File(String.format("%s/border_%03d.wkt",export_folder,i));
				if(icyAssert(expected_wkt_file.exists(),"Missing border file: " + expected_wkt_file))
					return true;
			}
			
		}
		
		//Check tracking files if selected
		if(varDoTracking.getValue()){
			
			if(icyAssert(input_file_paths.length > 1, 
					"Tracking requires at least two time points! Please increase time points to load"))
				return true;
			
			
			if(varTrackingAlgorithm.getValue() == TrackingEnum.LOAD_CSV_FILE){
				if(icyAssert(varLoadFile.getValue() != null,
						"Load CSV tracking feature requires an input directory: please review!"))
					return true;
				
				File input_directory = varLoadFile.getValue();
				
				if(icyAssert(input_directory.isDirectory(), "Input directory is not valid, please review"))
					return true;
				
				for(int i=0; i < varMaxT.getValue(); i++){
					File tracking_file = new File(input_directory, String.format(CsvTrackReader.tracking_file_pattern, i));
					if(icyAssert(tracking_file.exists(), "Missing tracking file: "+tracking_file.getAbsolutePath()))
						return true;
				}
				
				File division_file = new File(input_directory, CsvTrackReader.division_file_pattern);
				if(icyAssert(division_file.exists(), "Missing division file: "+division_file.getAbsolutePath()))
					return true;
				
				File elimination_file = new File(input_directory, CsvTrackReader.elimination_file_pattern);
				if(icyAssert(elimination_file.exists(), "Missing elimination file: "+elimination_file.getAbsolutePath()))
					return true;
			}
		}

		return false;
	}
	
	/**
	 * Returns an ICY alert message if the condition is false
	 * 
	 * @param condition a boolean condition to check
	 * @param error_message a message to display if the condition is false
	 * @return true if the condition is wrong and alert was issued, false if condition is true
	 */
	private boolean icyAssert(boolean condition, String error_message) {
		
		if(condition){
			return false;
		}
		else{
			new AnnounceFrame(error_message,10);
			return true;
		}
	}


	/**
	 * Removes all current overlays from the input sequence
	 */
	private void removeAllOverlays() {
		
		List<Overlay> overlays = sequence.getOverlays();
		for (Overlay overlay : overlays) {
			sequence.removeOverlay(overlay);
			sequence.overlayChanged(overlay);       				
		}
	}

	/**
	 * Generates a spatio-temporal graph (stGraph) from the input files
	 * 
	 * @param input_file_paths input files for the single frames of the stGraph
	 * @return a populated spatio-temporal graph 
	 */
	private SpatioTemporalGraph generateSpatioTemporalGraph(String[] input_file_paths) {
		
		GraphType graph_type = GraphType.TISSUE_EVOLUTION;
		InputType input_type = varInput.getValue();
		
		SpatioTemporalGraphGenerator stGraphGenerator = 
				new SpatioTemporalGraphGenerator(graph_type,input_type);
		
		this.getUI().setProgressBarMessage("Creating Spatial Graphs...");
		for(int i = 0; i< input_file_paths.length; i++){
			
			if(stopFlag){
				stopFlag = false;
				return stGraphGenerator.getStGraph();
			}
			
			stGraphGenerator.addFrame(i, input_file_paths[i]);
			this.getUI().setProgressBarValue(i/(double)input_file_paths.length);
		}
		this.getUI().setProgressBarValue(0);
		
		return stGraphGenerator.getStGraph();
	}

	/**
	 * Applies the border conditions to the graph
	 * 
	 * @param stGraph the graph to be analyzed
	 */
	private void applyBorderOptions(SpatioTemporalGraph stGraph) {
		
		this.getUI().setProgressBarMessage("Setting Boundary Conditions...");

		BorderCells border = new BorderCells(stGraph);
		
		if(varInput.getValue() == InputType.WKT){
			//assumes the border files are in the same directory of the wkt skeletons
			String export_folder = varFile.getValue().getParent();
			border.markBorderCellsWKT(export_folder);
		}
		else{
			if(varCutBorder.getValue())
				border.removeOneBoundaryLayerFromAllFrames();
			else
				border.markOnly();

			//removing outer layers of first frame to ensure more accurate tracking
			if(varDoTracking.getValue() && 
					varTrackingAlgorithm.getValue() != TrackingEnum.LOAD_CSV_FILE)
				for(int i=0; i < varBorderEliminationNo.getValue();i++)
					border.removeOneBoundaryLayerFromFrame(0);
		}
	
	}

	/**
	 * Applies a tracking algorithm to the graph
	 * 
	 * @param stGraph
	 */
	private void applyTracking(SpatioTemporalGraph stGraph){

		this.getUI().setProgressBarMessage("Tracking Graphs...");

		TrackingAlgorithm tracker = null;

		switch(varTrackingAlgorithm.getValue()){
		case STABLE_MARRIAGE:
			tracker = new StableMarriageTracking(
					stGraph, 
					varLinkrange.getValue(),
					varLambda1.getValue(),
					varLambda2.getValue());
			break;
		case HUNGARIAN:
			tracker = new HungarianTracking(
					stGraph, 
					varLinkrange.getValue(),
					varLambda1.getValue(),
					varLambda2.getValue());
			break;
		case LOAD_CSV_FILE:
			String output_folder = varLoadFile.getValue().getAbsolutePath();
			tracker = new CsvTrackReader(stGraph, output_folder);
			break;
		}

		// TODO try&catch
		tracker.track();
		stGraph.setTracking(true);

		paintTrackingResult(stGraph);

	}

	/**
	 * Add an overlay to the sequence displaying the tracking results
	 * 
	 * @param stGraph the graph whose tracking to display
	 */
	private void paintTrackingResult(SpatioTemporalGraph stGraph) {
		if(varBooleanCellIDs.getValue()){
			Overlay trackID = new TrackIdOverlay(stGraph);
			sequence.addOverlay(trackID);
		}
		
		if(varBooleanDrawDisplacement.getValue()){
			Overlay displacementSegments = new DisplacementOverlay(stGraph, varDisplacement.getValue());
			sequence.addOverlay(displacementSegments);
		}

		//Paint corresponding cells in time
		TrackingOverlay correspondence = new TrackingOverlay(stGraph,varBooleanHighlightMistakesBoolean.getValue());
		sequence.addOverlay(correspondence);
		
	}

	/**
	 * Add the spatio-temporal graph structure to the ICY swimming pool, the shared
	 * memory structure that can be accessed by all plugins<br><br>
	 * 
	 * <b>Warning: Currently only one graph at a time is allowed in the structure</b> 
	 * 
	 * @param stGraph the graph to add to the swiming pool
	 */
	private void pushToSwimingPool(SpatioTemporalGraph stGraph) {
		
		SwimmingPool icySP = Icy.getMainInterface().getSwimmingPool();
		
		//remove all formerly present stGraph objects 
		for(SwimmingObject swimmingObject: icySP.getObjects())
			if ( swimmingObject.getObject() instanceof SpatioTemporalGraph )
				icySP.remove(swimmingObject);
			
		// Put my object in a Swimming Object
		SwimmingObject swimmingObject = new SwimmingObject(stGraph,"stGraph");
		
		// add the object in the swimming pool
		icySP.add( swimmingObject );
		
	}

	@Override
	public void clean()
	{
		// use this method to clean local variables or input streams (if any) to avoid memory leaks
	}
	
	@Override
	public void stopExecution()
	{
		// this method is from the EzStoppable interface
		// if this interface is implemented, a "stop" button is displayed
		// and this method is called when the user hits the "stop" button
		stopFlag = true;
		
	}
	
}

