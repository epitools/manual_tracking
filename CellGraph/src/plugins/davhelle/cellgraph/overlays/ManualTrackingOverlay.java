package plugins.davhelle.cellgraph.overlays;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D.Double;

import icy.canvas.IcyCanvas;
import icy.gui.frame.progress.AnnounceFrame;
import icy.main.Icy;
import icy.sequence.Sequence;
import jxl.write.WritableSheet;
import plugins.davhelle.cellgraph.graphs.FrameGraph;
import plugins.davhelle.cellgraph.graphs.SpatioTemporalGraph;
import plugins.davhelle.cellgraph.nodes.Node;
import plugins.kernel.canvas.VtkCanvas;

public class ManualTrackingOverlay extends StGraphOverlay {

	public ManualTrackingOverlay(SpatioTemporalGraph stGraph) {
		super("Manual Tracking", stGraph);
		
		
		new AnnounceFrame("This will be a very cool manual tracking overlay, stay tuned!:-)");
		
	}
	
	@Override
	public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
    {
		if (canvas instanceof VtkCanvas)
			return;
		
		int time_point = Icy.getMainInterface().getFirstViewer(sequence).getPositionT();

		//Take previous time point
		time_point--;
		
		if(time_point >= 0 && time_point < stGraph.size()){
			FrameGraph frame_i = stGraph.getFrame(time_point);
			paintFrame(g, frame_i);
		}
		
		if(super.isLegendVisible())
			paintLegend(g,sequence,canvas);
		
    }

	@Override
	public void paintFrame(Graphics2D g, FrameGraph frame_i) {
		
		for(Node n: frame_i.vertexSet()){
			if(n.hasColorTag()){
				g.setColor(new Color(255,0,0,180));
				g.fill(n.toShape());
			}
		}

	}

	@Override
	public void specifyLegend(Graphics2D g, Double line) {
		// TODO Auto-generated method stub

	}

	@Override
	void writeFrameSheet(WritableSheet sheet, FrameGraph frame) {
		// TODO Auto-generated method stub

	}

}
