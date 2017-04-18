package plugins.davhelle.cellgraph.io;

import icy.gui.dialog.SaveDialog;
import icy.gui.frame.progress.AnnounceFrame;
import icy.system.IcyExceptionHandler;
import icy.util.XLSUtil;

import java.io.IOException;

import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Node;

/**
 * Class to export summary excel sheet that contains statistics
 * for each frame, such as cell number, area and detected division events
 * 
 * @author Davide Heller
 *
 */
public class SummaryWriter {
	public static final String DESCRIPTION = "Summary Excel sheet containing statistics<br/>" +
			"for each frame. Currently included are:<br/><ul>" +
			"<li>Total number of cells" +
			"<li>Total area of all cells" +
			"<li>Cells dividing in the next frame" +
			"<li>Cells eliminated in the next frame</ul>";
	
	private SpatioTemporalGraph stGraph;
	
	public SummaryWriter(SpatioTemporalGraph stGraph){
		this.stGraph = stGraph;
	}
	
	public void writeXLSFile(){
		try {
			String file_name = SaveDialog.chooseFile(
					"Please choose where to save the excel Sheet",
					"/Users/davide/",
					"summary_file", XLSUtil.FILE_DOT_EXTENSION);
			
			if(file_name == null)
				return;
				
			WritableWorkbook wb = XLSUtil.createWorkbook(file_name);
			WritableSheet sheet = XLSUtil.createNewPage(wb, "Summary");
			writeSummarySheet(sheet);
			
			XLSUtil.saveAndClose(wb);
			
			new AnnounceFrame("XLS file exported successfully to: "+file_name,10);
			
		} catch (WriteException writeException) {
			IcyExceptionHandler.showErrorMessage(writeException, true, true);
		} catch (IOException ioException) {
			IcyExceptionHandler.showErrorMessage(ioException, true, true);
		}
	}

	private void writeSummarySheet(WritableSheet sheet) {
		
		int row_no = 0;
		int col_no = 0;
		
		//rows = frames
		//cols = frameNo;cells;area;divisions;eliminations;border
		
		XLSUtil.setCellString(sheet, col_no++, row_no, "Frame.No");
		XLSUtil.setCellString(sheet, col_no++, row_no, "Tot.Cells");
		XLSUtil.setCellString(sheet, col_no++, row_no, "Tot.Area");
		XLSUtil.setCellString(sheet, col_no++, row_no, "Divisions");
		XLSUtil.setCellString(sheet, col_no++, row_no, "Eliminations");
		
		row_no++;
		
		for(int i=0; i<stGraph.size(); i++){
			
			FrameGraph frame = stGraph.getFrame(i);
			
			//reset column
			col_no = 0;
			
			XLSUtil.setCellNumber(sheet, col_no++, row_no, i);
			XLSUtil.setCellNumber(sheet, col_no++, row_no, frame.size());
			
			double totArea = 0.0;
			int divisionsInFrame = 0;
			int eliminationsInFrame = 0;
			for(Node n: frame.vertexSet()){
				
				totArea += n.getGeometry().getArea();
				
				if(n.hasObservedDivision())
					if(n.getDivision().getTimePoint() == i+1)
						divisionsInFrame++;
				
				if(n.hasObservedElimination())
					if(n.getElimination().getTimePoint() == i)
						eliminationsInFrame++;
			}
			
			XLSUtil.setCellNumber(sheet, col_no++, row_no, totArea);
			XLSUtil.setCellNumber(sheet, col_no++, row_no, divisionsInFrame);
			XLSUtil.setCellNumber(sheet, col_no++, row_no, eliminationsInFrame);
			
			//increase row
			row_no++;
		}

		
		
	}
}
