package plugins.davhelle.cellgraph.painters;

import icy.canvas.IcyCanvas;
import icy.main.Icy;
import icy.painter.AbstractPainter;
import icy.sequence.Sequence;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;

public class ShapePainter extends AbstractPainter{
	
	private Shape shape;
	private int time_point;
	private Color cell_color = Color.green;
	
	public ShapePainter(Shape shape, int time_point) {
		this.shape = shape;
		this.time_point = time_point;
	}
	
	public void setColor(Color new_color){
		cell_color = new_color;
	}
	
	@Override
	public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
	{
		//only display when on selected frame
		if(Icy.getMainInterface().getFirstViewer(sequence).getT() == time_point){
			//Initialize painter
			g.setStroke(new BasicStroke(1));
			g.setColor(cell_color);

			//Complete diagram
			g.fill(shape);
		}
	}

}