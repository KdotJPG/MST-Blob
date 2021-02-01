/**
 * Creates a connected blob-like image by analytically integrating the edges of a Minimum Spanning Tree through a blur kernel, and thresholding.
 * Inspired by https://www.reddit.com/r/proceduralgeneration/comments/dbtk88/looking_of_ideasadvice_for_generating_this_sort/
 *
 * @author K.jpg
 */

import java.util.Random;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.WindowConstants;

public class MSTBlob {
	
	//Change these around
	private static final int SEED = 385926;
	private static final int RESOLUTION = 2048;
	private static final double POINT_MIN_SPACING = 0.05;
	private static final double KERNEL_RADIUS_MULTIPLIER = 1;
	private static final double THRESHOLD = .4;
	private static final boolean VIEW_POINTS = false;
	private static final boolean VIEW_EDGES = false;
	private static final boolean RAW_OUTPUT = false;
	
	//Set this if you want to save a PNG image (e.g. "blob.png"), null otherwise
	private static String FILENAME_TO_SAVE = null;
	
	//Can turn this off if you're trying to save an image with a massive resolution
	private static boolean DISPLAY_IN_UI = true;
	
	//Probably don't need to change these
	private static final double KERNEL_RADIUS = KERNEL_RADIUS_MULTIPLIER * POINT_MIN_SPACING;
	private static final double ZOOM_OUT = (1 + KERNEL_RADIUS);
	private static final double RENDER_POINT_THICKNESS_SQ = (1d / 256) * (POINT_MIN_SPACING / .05);
	private static final double RENDER_EDGE_THICKNESS_SQ = (1d / 512) * (POINT_MIN_SPACING / .05);
	private static final int N_TIMES_TO_TRY_PLACING = (int)(1 / (POINT_MIN_SPACING * POINT_MIN_SPACING));
	
	//Don't need to change these
	private static final int KRUSKAL_NO_GROUP = -1;
	private static final int KRUSKAL_REJECTED = -2;
	
	public static void main(String[] args) throws java.io.IOException {
		
		//Generate points. Kind of an inefficient and poorer quality brute force "poisson disk sampling" esque thing that just rejects bad points.
		// Can be improved.
		Point[] points = null;
		{
			//You can ignore the parameter here. I gave the ArrayList an initial size based on circle packing ratio math, just for fun. It's an upper bound.
			ArrayList<Point> pointsBuffer = new ArrayList<Point>((int)(0.9069 / 4 / POINT_MIN_SPACING / POINT_MIN_SPACING));
			Random random = new Random(SEED);
			int j = 0;
			for (int i = 0; i < N_TIMES_TO_TRY_PLACING; i++) {
				Point point = new Point();
				point.x = random.nextDouble();
				point.y = random.nextDouble();
				
				//First, let's keep our points within a circle centered on the square. Not a poisson thing, just something I decided to do.
				if ((point.x - .5) * (point.x - .5) + (point.y - .5) * (point.y - .5) > .25) continue;
				
				//Throw it out if it's too close to any previously chosen point (the inefficient part)
				boolean rejected = false;
				for (int k = 0; k < j; k++) {
					Point other = pointsBuffer.get(k);
					if ((other.x - point.x) * (other.x - point.x) + (other.y - point.y) * (other.y - point.y) < POINT_MIN_SPACING * POINT_MIN_SPACING) {
						rejected = true;
						break;
					}
				}
				if (rejected) {
					continue;
				}
				
				pointsBuffer.add(point);
				j++;
			}
			
			//Convert to array because I already coded the rest of it to use an array, and I don't feel like changing it right now.
			points = pointsBuffer.toArray(new Point[j]);
		}
		System.out.println("Generated points: " + points.length);
		
		//Prep for Kruskal's: Enumerate all possible edges
		// Would be better to compute a Delaunay triangulation, I think
		Edge[] edges = new Edge[points.length * (points.length - 1) / 2];
		{
			int k = 0;
			for (int i = 0; i < points.length; i++) {
				for (int j = i + 1; j < points.length; j++) {
					Edge edge = new Edge();
					edge.a = points[i];
					edge.b = points[j];
					edge.weight = (edge.a.x - edge.b.x) * (edge.a.x - edge.b.x) + (edge.a.y - edge.b.y) * (edge.a.y - edge.b.y);
					edges[k] = edge;
					k++;
				}
			}
		}
		System.out.println("Generated edges: " + edges.length);
		
		//Kruskal's: Sort edges
		Arrays.sort(edges, new Comparator<Edge>() {
			public int compare(Edge e1, Edge e2) {
				return (int)Math.signum(e1.weight - e2.weight);
			}
		});
		System.out.println("Sorted edges");
		
		//Kruskal's: Generate tree
		List<Edge> tree = new ArrayList<Edge>();
		int treePoints = 0;
		int groupCounter = 0;
		for (int i = 0; i < edges.length; i++) {
			Edge newEdge = edges[i];
			
			//Check if the edge connects two different groups, or would create a cycle
			int groupA = KRUSKAL_NO_GROUP;
			int groupB = KRUSKAL_NO_GROUP;
			for (int j = 0; j < i; j++) {
				Edge pastEdge = edges[j];
				if (pastEdge.group == KRUSKAL_REJECTED) continue;
				if (newEdge.a == pastEdge.a || newEdge.a == pastEdge.b) groupA = pastEdge.group;
				if (newEdge.b == pastEdge.a || newEdge.b == pastEdge.b) groupB = pastEdge.group;
				if (groupA >= 0 && groupB >= 0) break;
			}
			
			if (groupA == KRUSKAL_NO_GROUP || groupB == KRUSKAL_NO_GROUP || groupA != groupB) {
				int thisGroup = KRUSKAL_NO_GROUP;
				if (groupA != KRUSKAL_NO_GROUP) {
					thisGroup = groupA;
				} else if (groupB != KRUSKAL_NO_GROUP) {
					thisGroup = groupB;
				} else {
					thisGroup = groupCounter;
					groupCounter++;
				}
				
				//Union
				for (int j = 0; j < i; j++) {
					if (edges[j].group == groupA || edges[j].group == groupB) edges[j].group = thisGroup;
				}
				newEdge.group = thisGroup;
				tree.add(newEdge);
				
				//So we can go ahead and stop once the tree is complete
				if (treePoints >= points.length) break;
				if (groupA == KRUSKAL_NO_GROUP) treePoints++;
				if (groupB == KRUSKAL_NO_GROUP) treePoints++;
				
			} else {
				newEdge.group = KRUSKAL_REJECTED;
			}
		}
		System.out.println("Generated tree, edges: " + tree.size());
		
		//Generate image
		BufferedImage image = new BufferedImage(RESOLUTION, RESOLUTION, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < RESOLUTION; y++) {
				double yy = y * 1d / RESOLUTION;
				yy = ((yy - .5) * ZOOM_OUT) + .5; //zoom out a bit
			for (int x = 0; x < RESOLUTION; x++) {
				double xx = x * 1d / RESOLUTION;
				xx = ((xx - .5) * ZOOM_OUT) + .5; //zoom out a bit
				
				double value = 0;
				
				if (VIEW_POINTS) {
					for (int i = 0; i < points.length; i++) {
						Point p = points[i];
						double dx = p.x - xx;
						double dy = p.y - yy;
						double dSq = (dx * dx) + (dy * dy);
						if (dSq < RENDER_POINT_THICKNESS_SQ * RENDER_POINT_THICKNESS_SQ) value = .75;
					}
				}
				
				if (VIEW_EDGES && value == 0) {
					for (Edge edge : tree) {
						//Distance to line segment
						// https://stackoverflow.com/questions/849211/shortest-distance-between-a-point-and-a-line-segment
						// http://paulbourke.net/geometry/pointlineplane/
						double px = edge.b.x - edge.a.x;
						double py = edge.b.y - edge.a.y;
						double norm = (px * px) + (py * py);
						double t = ((xx - edge.a.x) * px + (yy - edge.a.y) * py) / norm;
						if (t > 1) t = 1;
						if (t < 0) t = 0;
						double sx = edge.a.x + t * px;
						double sy = edge.a.y + t * py;
						double segDistSq = (xx - sx) * (xx - sx) + (yy - sy) * (yy - sy);
						if (segDistSq < RENDER_EDGE_THICKNESS_SQ * RENDER_EDGE_THICKNESS_SQ) value = .5;
					}
				}
				
				//Go through edges to find which ones intersect our kernel
				if (value == 0) {
					for (Edge edge : tree) {
						
						//Parameterization of line segment: rates of change in X and Y
						double dx = edge.b.x - edge.a.x;
						double dy = edge.b.y - edge.a.y;
						
						//Line segment start point, relative to current kernel center (pixel location)
						double rx = edge.a.x - xx;
						double ry = edge.a.y - yy;
						
						//Quadratic equation of where line intersects circle
						//Also sets us up to integrate our blur kernel later.
						// x2 + y2 = r2, x = at + b, y = ct + d
						// (at + b)2 + (ct + d)2 = r2
						// a2t2 + 2abt + b2 + c2t2 + 2cdt + d2 = r2
						// (a2 + c2)t2 + 2(ab + cd)t + (b2 + d2 - r2) = 0
						double qa = dx * dx + dy * dy;
						double qb = 2 * (dx * rx + dy * ry);
						double qc = rx * rx + ry * ry - KERNEL_RADIUS * KERNEL_RADIUS;
						double qSqrtInside = qb * qb - 4 * qa * qc;
						
						//Line doesn't intersect kernel (I wonder at what point it becomes more efficient to store segments in a lookup tree)
						if (qSqrtInside < 0) continue;
						
						double qSqrtPart = Math.sqrt(qSqrtInside);
						double t1 = (-qb - qSqrtPart) / (2 * qa);
						double t2 = (-qb + qSqrtPart) / (2 * qa);
						
						//The line intersects, but the segment doesn't. Again, I wonder about that lookup tree.
						if (t2 <= 0 || t1 >= 1) continue;
						
						//Don't go all the way to kernel edge if our line segment starts or ends inside of it
						if (t1 < 0) t1 = 0;
						if (t2 > 1) t2 = 1;
						
						//Re-use circle intersection quadratic to integrate kernel equation (r2 - x2 - y2)^2
						// this is the integral from t1 to t2 when you plug the line segment into this kernel equation.
						// type "integral (a*t^2+b*t+c)^2 dt" into Wolfram Alpha.
						double i2 = t2 * (t2 * (t2 * (t2 * (t2 * qa * qa / 5 + qa * qb / 2) + (2 * qa * qc + qb * qb) / 3) + qb * qc) + qc * qc);
						double i1 = t1 * (t1 * (t1 * (t1 * (t1 * qa * qa / 5 + qa * qb / 2) + (2 * qa * qc + qb * qb) / 3) + qb * qc) + qc * qc);
						double integrationResult = i2 - i1;
						
						//Divide by radius four times to account for r inside equation,
						// and once to account for longer lengths of segments that it sees when it gets bigger
						integrationResult /= KERNEL_RADIUS * KERNEL_RADIUS * KERNEL_RADIUS * KERNEL_RADIUS * KERNEL_RADIUS;
						
						//Multiply by length of line segment, so that longer segments are fairly represented.
						// Doesn't affect when a segment is cut off at the kernel boundary, that's already accounted for by t1, t2.
						// I wonder what it does if we take this out...
						integrationResult *= Math.sqrt(qa);
						
						value += integrationResult;
						
					}
					if (RAW_OUTPUT) {
						//I didn't compute this value analytically. It might be too big or there might be seeds that cause the result to exceed 1.
						value /= 2.5;
						
						//So just in case, don't let it exceed 1.
						if (value > 1) value = 1;
					} else {
						value = value > THRESHOLD ? 0 : 1;
					}
				
				}
				Color color = Color.getHSBColor(1.0f, 0.0f, (float) value);
				image.setRGB(x, y, color.getRGB());
			}
		}
		System.out.println("Generated image");
		
		//Save it if we want to
		if (FILENAME_TO_SAVE != null) {
			ImageIO.write(image, "png", new File(FILENAME_TO_SAVE));
			System.out.println("Saved image");
		}
		
		//Display the image in a UI window
		if (DISPLAY_IN_UI) {
			JFrame frame = new JFrame();
			JLabel imageLabel = new JLabel();
			imageLabel.setIcon(new ImageIcon(image));
			frame.add(imageLabel);
			frame.pack();
			frame.setResizable(false);
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			frame.setVisible(true);
			System.out.println("Displaying image");
		}
	}
	
	private static class Point {
		double x, y;
	}
	
	private static class Edge {
		Point a, b;
		double weight;
		int group;
	}
	
}