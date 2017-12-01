package plugins.davhelle.cellgraph.overlays;

import icy.util.XLSUtil;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Line2D.Double;

import jxl.write.WritableSheet;
import plugins.adufour.ezplug.EzVar;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarListener;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Node;

/**
 * Painter to highlight the cells that lie on the boundary in white 
 * 
 * @author Davide Heller
 *
 */
public class BorderOverlay extends StGraphOverlay implements EzVarListener<Boolean>{
	
	
		EzVarBoolean showDisplacement;

		/**
		 * Descriptor String for GUI use
		 */
		public static final String DESCRIPTION = 
				"Overlay to show where the border of the segmentation <br/>" +
				"was identified and optionally the displacement towards <br/>" +
				"the last frame";

		/**
		 * @param spatioTemporalGraph graph to be analyzed
		 * @param varBorderDisplacement 
		 */
		public BorderOverlay(SpatioTemporalGraph spatioTemporalGraph, EzVarBoolean varBorderDisplacement){
			super("Border cells",spatioTemporalGraph);
			showDisplacement = varBorderDisplacement;
			showDisplacement.addVarChangeListener(this);
		}
		
		/**
		 * Draw an arrow line between two points.
		 * @param g the graphics component.
		 * @param x1 x-position of first point.
		 * @param y1 y-position of first point.
		 * @param x2 x-position of second point.
		 * @param y2 y-position of second point.
		 * @param d  the width of the arrow.
		 * @param h  the height of the arrow.
		 * 
		 * source: https://stackoverflow.com/a/27461352
		 */
		private void drawArrowLine(Graphics2D g, int x1, int y1, int x2, int y2, int d, int h) {
		    int dx = x2 - x1, dy = y2 - y1;
		    double D = Math.sqrt(dx*dx + dy*dy);
		    double xm = D - d, xn = xm, ym = h, yn = -h, x;
		    double sin = dy / D, cos = dx / D;

		    x = xm*cos - ym*sin + x1;
		    ym = xm*sin + ym*cos + y1;
		    xm = x;

		    x = xn*cos - yn*sin + x1;
		    yn = xn*sin + yn*cos + y1;
		    xn = x;

		    int[] xpoints = {x2, (int) xm, (int) xn};
		    int[] ypoints = {y2, (int) ym, (int) yn};

		    g.drawLine(x1, y1, x2, y2);
		    g.fillPolygon(xpoints, ypoints, 3);
		}

		@Override
		public void paintFrame(Graphics2D g, FrameGraph frame_i) {
			
			for(Node cell: frame_i.vertexSet()){
				if(cell.onBoundary()){
					g.setColor(Color.white);
					
					Stroke old = g.getStroke();
					g.setStroke(new BasicStroke(2));
					g.draw(cell.toShape());
					g.setStroke(old);
					
					if(showDisplacement.getValue()){
						Node last = cell.getNext();
						if(last !=	null){
							while(last.hasNext())
								last = last.getNext();
						
							g.setColor(Color.red);
							//g.draw(last.toShape());
							
	//						g.setStroke(new BasicStroke(4));
	//						g.drawLine(
							drawArrowLine(g,
									(int)cell.getGeometry().getCentroid().getX(),
									(int)cell.getGeometry().getCentroid().getY(), 
									(int)last.getGeometry().getCentroid().getX(), 
									(int)last.getGeometry().getCentroid().getY(),3,3);
	//						g.setStroke(new BasicStroke(3));
						}
					}
				}
			}
		}

		@Override
		void writeFrameSheet(WritableSheet sheet, FrameGraph frame) {
			
			XLSUtil.setCellString(sheet, 0, 0, "Cell id");
			XLSUtil.setCellString(sheet, 1, 0, "On Border");

			int row_no = 1;
			for(Node node: frame.vertexSet()){
				XLSUtil.setCellNumber(sheet, 0, row_no, node.getTrackID());
				
				String booleanString = String.valueOf(node.onBoundary()).toUpperCase();
				XLSUtil.setCellString(sheet, 1, row_no, booleanString);

				row_no++;
			}
		}

		@Override
		public void specifyLegend(Graphics2D g, Double line) {
			
			String s = "Border Cells";
			Color c = Color.white;
			int offset = 0;
			
			OverlayUtils.stringColorLegend(g, line, s, c, offset);
			
		}

		@Override
		public void variableChanged(EzVar<Boolean> source, Boolean newValue) {
			painterChanged();
			
		}
}
