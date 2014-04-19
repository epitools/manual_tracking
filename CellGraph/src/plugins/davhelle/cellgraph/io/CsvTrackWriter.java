/*=========================================================================
 *
 *  (C) Copyright (2012-2014) Basler Group, IMLS, UZH
 *  
 *  All rights reserved.
 *	
 *  author:	Davide Heller
 *  email:	davide.heller@imls.uzh.ch
 *  
 *=========================================================================*/
package plugins.davhelle.cellgraph.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import plugins.davhelle.cellgraph.graphexport.ExportFieldType;
import plugins.davhelle.cellgraph.graphexport.VertexLabelProvider;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Node;


/**
 * Tracking File generator to save the tracking
 * generated by CellGraph. Uses the same output
 * as the GraphML export. I.e. the csv format.
 * 
 * @author Davide Heller
 *
 */
public class CsvTrackWriter {
	
	SpatioTemporalGraph wing_disc_movie;
	String output_directory;

	public CsvTrackWriter(SpatioTemporalGraph wing_disc_movie,String output_directory) {
		 this.wing_disc_movie = wing_disc_movie;
		 this.output_directory = output_directory;
	}
	
	public void writeTrackingIds(){
		
		for(int i=0; i < wing_disc_movie.size(); i++){
			
			FrameGraph frame = wing_disc_movie.getFrame(i);
			String file_name = output_directory + String.format("tracking_t%03d.csv",i);
			File output_file = new File(file_name);
			write(frame,output_file,ExportFieldType.TRACKING_POSITION);
		}
		
	}
	
	public void writeDivisions(){
		if(wing_disc_movie.size() > 0){
			FrameGraph frame = wing_disc_movie.getFrame(0);
			String file_name = output_directory + String.format("divisions.csv");
			File output_file = new File(file_name);
			write(frame,output_file,ExportFieldType.DIVISION);
		}
	}
	
	public void writeEliminations(){
		if(wing_disc_movie.size() > 0){
			FrameGraph frame = wing_disc_movie.getFrame(0);
			String file_name = output_directory + String.format("eliminations.csv");
			File output_file = new File(file_name);
			write(frame,output_file,ExportFieldType.ELIMINATION);
		}
	}
	
	private void write(FrameGraph frame, File output_file, ExportFieldType export_information){
		VertexLabelProvider tracking_information_provider = new VertexLabelProvider(export_information);
		
		try {

			// if file doesn't exists, then create it
			if (!output_file.exists()) {
				output_file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(output_file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			
			for(Node cell: frame.vertexSet()){
				String node_string = tracking_information_provider.getVertexName(cell);
				if(node_string.length() > 0){
					bw.write(node_string);
					bw.newLine();
				}
			}
			
			bw.close();
 
		} catch (IOException e) {
			e.printStackTrace();
		}			
	}
}
