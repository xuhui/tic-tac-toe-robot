/**
 * This class does the work to determine, via several steps, the type of cell in game board image.
 * 
 * The overall approach is defined in the method findCellType.
 * 
 * THERE ARE LOTS OF TUNING PAMRAMETERS THAT MIGHT HAVE TO BE TWEAKED FOR LIGHTING CONDITIONS!
 * 
 */
package org.gaf.ttt.image_analysis;

import org.gaf.ttt.common.TicTacToeGameBoard;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

public class CellTypeDetectorColor {

	private static final boolean DEBUG = true;
//	private static final boolean DEBUG = false;

	// cell types and status
	public static final int NO_REG = -100;


	/**
	 * Find the edges of circles in cell. The edges come from the registration circle, which
	 * should always be present, and the token disc itself and sometimes, but not often, the 
	 * circles in a naught token.
	 * 
	 * @param src color image of a cell
	 * @return a gray scale with the edges
	 */
	public Mat findEdges(Mat src) {

		double lowThreshold = 50.0;
		int ratio = 3;
		int kernel_size = 3;

		// create gray scale of original
		Mat gray = new Mat();
		Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

		// find edges
		Mat edges = new Mat();

		// blur to reduce noise with a kernel 3x3
		Imgproc.blur(gray, edges, new Size(3, 3));
		if (DEBUG) Highgui.imwrite("blur1.png", gray);

		// use Canny detector to find the edges
		Imgproc.Canny(edges, edges, lowThreshold, lowThreshold * ratio, kernel_size, true);

		// output the debug image
		if (DEBUG) Highgui.imwrite("temp.png", edges);

		// return the edges
		return edges;
	}

	/** 
	 * Finds the registration circle that should always exist in a cell.
	 * 
	 * The primary discrimination is based on the radius of circle(s) found. If one is found,
	 * that is returned. If more than one (often get 2), the "average" is returned.
	 * 
	 * @param edges a gray scale with the edges
	 * @return a triple of the center (x,y) and radius of the circle
	 */
	public double[] findCircleReg(Mat edges) {

		// min and max radius for registration circle
		int radiusMin = 65;
		int radiusMax = 85;

		// create holder for blurred
		Mat blurred = new Mat();

		// blur
		Imgproc.GaussianBlur( edges, blurred, new Size(9., 9.), 2, 2 );	
		//		Imgproc.GaussianBlur( edges, blurred, new Size(5., 5.), 0, 0 );	
		if (DEBUG) Highgui.imwrite("blur2.png", blurred);

		// find circle(s)
		Mat circles = new Mat();
		Imgproc.HoughCircles(blurred, circles, Imgproc.CV_HOUGH_GRADIENT, 1, 1, 200, 100, radiusMin, radiusMax);

		if (DEBUG) {
			System.out.println("number of circles: " + circles.total());		
			System.out.println("circles1: " + circles);
		}

		// figure out a single circle to return
		double[] circle = null;
		if (circles.total() == 0) { // return nothing
			return circle;
		} else if (circles.total() == 1) { // return whatever get
			circle = circles.get(0,0);
		} else { // then have to make a single circle
			// accumulate values
			double cx = 0;
			double cy = 0;
			double cr = 0;			
			for (int i = 0; i < circles.total(); i++) {				
				double[] vector = circles.get(0,i);				
				if (DEBUG) System.out.println("circle center: " + vector[0] + ", " + vector[1] + " radius: " + vector[2]);
				cx += vector[0];
				cy += vector[1];
				cr += vector[2];	
			}
			// get average circle
			double[] average = {cx/circles.total(), cy/circles.total(), cr/circles.total()};
			if (DEBUG) System.out.println("average center: " + average[0] + ", " + average[1] + " radius: " + average[2]);
			circle = average;
		}

		return circle;
	}

	/**
	 * Finds a token in the cell, inside the registration circle. 
	 * 
	 * The primary discrimination is based on the radius of circle(s) found. If one is found,
	 * that is returned. If more than one (often get 2), the "average" is returned.
	 * 
	 * @param edges  a gray scale with the edges
	 * @return a triple of the center (x,y) and radius of the circle
	 */
	public double[] findCircleDisc(Mat edges) {
		// min and max radius for disc circle
		int radiusMin = 40;
		int radiusMax = 65;

		// create holder for blurred
		Mat blurred = new Mat();

		// blur
		Imgproc.GaussianBlur( edges, blurred, new Size(9., 9.), 2, 2 );	
		//		Imgproc.GaussianBlur( edges, blurred, new Size(5., 5.), 0, 0 );	
		if (DEBUG) Highgui.imwrite("blur2.png", blurred);

		// find circles
		Mat circles = new Mat();	
		Imgproc.HoughCircles(blurred, circles, Imgproc.CV_HOUGH_GRADIENT, 1, 20, 200, 50, radiusMin, radiusMax);

		if (DEBUG) {
			System.out.println("number of circles: " + circles.total());
			System.out.println("circles2: " + circles);
		}

		// figure out a single circle to return
		double[] circle = null;
		if (circles.total() == 0) { // return nothing
			return circle;
		} else if (circles.total() == 1) { // return whatever get
			circle = circles.get(0,0);
		} else { // then have to make a single circle
			// accumulate values
			double cx = 0;
			double cy = 0;
			double cr = 0;			
			for (int i = 0; i < circles.total(); i++) {				
				double[] vector = circles.get(0,i);				
				if (DEBUG) System.out.println("circle center: " + vector[0] + ", " + vector[1] + " radius: " + vector[2]);
				cx += vector[0];
				cy += vector[1];
				cr += vector[2];				
			}			
			double[] average = {cx/circles.total(), cy/circles.total(), cr/circles.total()};
			if (DEBUG) System.out.println("average center: " + average[0] + ", " + average[1] + " radius: " + average[2]);
			circle = average;
		}

		return circle;
	}

	/**
	 * Finds the mean color in the center of a token. If more red than black, have a Naught.
	 * 
	 * @param src original cell image (color)
	 * @param center of the token
	 * @return the mean color (a triplet) of a circle at the center of a token
	 */
	public Scalar findCellMean(Mat src, Point center) {
		
		int radius = 10; // determined empirically from the geometry of captured images

		// create Mat for mask; same size as input
		Mat mask = Mat.zeros(src.rows(), src.cols(), CvType.CV_8UC1);

		// draw circle in mask
		Core.circle(mask, center, radius, new Scalar(255), -1);
		if (DEBUG) {
			System.out.println("mask-" + mask);
			Highgui.imwrite("mask.png", mask);
		}

		// get the mean
		Scalar mean = Core.mean(src, mask);
		if (DEBUG) System.out.println("mean: " + mean);

		return mean;
	}

	/**
	 * Find the type of token in a cell, if one exists.
	 * 
	 * Process:
	 * 	- find edges in cell, which will mostly be the registration circle and the token
	 * 		if present; sometimes the board lines appear at least partially
	 * 	- find registration circle, primarily as a verification of image quality
	 * 	- find the token disc, if it exists
	 * 	- when token exists, find the mean color at the center of the token
	 * 	- if the center is more red than black, have O otherwise X
	 * 
	 * @param src the original cell image (color)
	 * @return type of cell
	 */
	public int findCellType(Mat src) {
		
		// start timing
		long mStartC = System.currentTimeMillis();

		
		// factor to distinguish equality of bgr values
		double factor = 2.5; // e.g., if b < factor * r and g < factor * r have red (O)

		int rc = 99;
		Mat draw = null; // for debug

		if (DEBUG) {
			// make copy of source for debugging
			draw = new Mat(); // for debug
			src.copyTo(draw);
		}

		// find edges
		Mat edges = findEdges(src);

		// find the registration circle
		double[] circleReg = findCircleReg(edges);
		if (circleReg == null) {
			System.out.println("NO Registration circle! Abort!");
			rc = CellTypeDetectorColor.NO_REG;
		} else {
			// got the reg circle
			Point cCenter = new Point(circleReg[0], circleReg[1]);
			if (DEBUG) {
				System.out.println("\nReg circle center: " + circleReg[0] + ", " + circleReg[1] + " radius: " + circleReg[2]);
				Core.circle(draw, cCenter, 4, new Scalar(0, 255, 0), -1, 8, 0);
				Core.circle( draw, cCenter, (int) circleReg[2], new Scalar(255,0,0), 2, 8, 0 );

				Core.circle(edges, cCenter, 4, new Scalar(0, 255, 0), -1, 8, 0);
				Core.circle( edges, cCenter, (int) circleReg[2], new Scalar(255,0,0), 2, 8, 0 );
			}

			// find inner circle (disc)
			double[] circleDisc = findCircleDisc(edges);
			if (circleDisc == null) {
				if (DEBUG) System.out.println("NO Disc circle!");
				rc = TicTacToeGameBoard.EMPTY;

			} else { // have a token
				// set the center of the disc
				cCenter = new Point(circleDisc[0], circleDisc[1]);
				if (DEBUG){
					System.out.println("\nDisc circle center: " + circleDisc[0] + ", " + circleDisc[1] + " radius: " + circleDisc[2]);
					Core.circle(draw, cCenter, 4, new Scalar(255, 255, 0), -1, 8, 0);
					Core.circle( draw, cCenter, (int) circleDisc[2], new Scalar(255,0,0), 2, 8, 0 );

					Core.circle(edges, cCenter, 4, new Scalar(255, 255, 0), -1, 8, 0);
					Core.circle( edges, cCenter, (int) circleDisc[2], new Scalar(255,255,0), 2, 8, 0 );
				}

				// find the cell mean for token (for either X or O)
				Scalar mean = findCellMean(src, cCenter);
				double [] bgra = mean.val;
				if ((factor * bgra[0]) < bgra[2] && (factor * bgra[1]) < bgra[2]) { 
					// have a red middle
					if (DEBUG) System.out.println("Think found NAUGHT");
					rc = TicTacToeGameBoard.NAUGHT;
				} else { 
					// have black middle
					if (DEBUG) System.out.println("Think found CROSS");
					rc = TicTacToeGameBoard.CROSS;
				}
			}

			// output the debug image
			if (DEBUG) {
				Highgui.imwrite("final.png", draw);
				Highgui.imwrite("temp2.png", edges);
			}

		}   
		
		
		long mStopC = System.currentTimeMillis();
//		if (DEBUGT)
			System.out.println("Cell type time millisec: "
					+ (mStopC - mStartC));

		return rc;
	}

	public static void main(String[] args) {
		System.out.println("Testing finding edges of circle, then circle");

		// Load the native library.
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		// load the source image
		Mat src = Highgui
				// .imread("/Users/gregflurry/Documents/Robotics/Arm/test_pics/ttt-test.png");
				// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell22.png");
				// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell21.png");
				// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/test-disc.tiff");
				// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/test-disc-red.tiff");
				// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/test-red-cir.tiff");
				// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/test-red-cir-off.tiff");
				.imread("/Users/gregflurry/Documents/workspaceArm/ArmPrime/cell11.png");
		// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell01.png");
		//		 .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell02.png");
		//				.imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell10.png");
		//		 .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell11.png");
		//		 .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell12.png");
		// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell20.png");
		// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell21.png");
		// .imread("/Users/gregflurry/Documents/workspaceArm/ImageProcess/cell22.png");

		System.out.println("input-" + src);
		Highgui.imwrite("outputUF.png", src);

		// get the finder
		CellTypeDetectorColor finder = new CellTypeDetectorColor();

		int type = finder.findCellType(src);

		System.out.println("Cell type: " + type);

		switch (type) {
		case CellTypeDetectorColor.NO_REG:
			System.out.println("NO Registration!");
			break;
		case TicTacToeGameBoard.EMPTY:
			System.out.println("Empty cell");
			break;
		case TicTacToeGameBoard.NAUGHT:
			System.out.println("O cell");
			break;
		case TicTacToeGameBoard.CROSS:
			System.out.println("X cell");
			break;
		}

	}


}
