/**
 * This groovy script is for analyzing bone growth around a metal implant. 
 * 
 * Script uses uncalibrated density data.
 * 
 * 
 * @author Andrew McCall
 **/

//Threshold values, in arbitrary intensity units
int minImageBone = 34000;
int maxImageBone = 35900;
int minImplant = 40020;
//

//All ranges are in pixel units
int contactAreaRange = 3;
int boneVolumeRange = 10;

Shape contactAreaShape = new RectangleShape(contactAreaRange, true);
Shape boneInRangeShape = new RectangleShape(boneVolumeRange, true);


//Utility shape, do no modify
Shape shape = new HyperSphereShape(3);

import net.imglib2.algorithm.binary.Thresholder;
import java.util.function.*;
import java.util.stream.LongStream;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import ij.plugin.FolderOpener;
import ij.ImagePlus;


import io.scif.config.SCIFIOConfig;

import net.imagej.axis.Axes;
import net.imagej.Dataset; 
import net.imagej.ImgPlus;


import net.imglib2.algorithm.neighborhood.*;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.logic.*;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.mask.integer.RandomAccessibleIntervalAsMaskInterval;
import net.imglib2.roi.*;
import net.imglib2.roi.labeling.*;
import net.imglib2.Point;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.imglib2.util.FlatCollections;

import org.scijava.plot.*;
import org.scijava.plot.defaultplot.*;
import org.scijava.table.Table;
import org.scijava.table.Tables;
import org.scijava.ui.swing.viewer.plot.jfreechart.CategoryChartConverter;


//Mesh imports

import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;
import customnode.CustomTriangleMesh;
import net.imagej.mesh.*;
import net.imagej.mesh.io.stl.STLMeshIO;
import ij3d.Image3DUniverse;


import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;

import java.io.File;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation

import com.google.common.collect.ImmutableMap;

#@ File (label="Select directory of DICOM directories", style = "directory") dicomDirectory
#@ File (label="Output directory", style="directory", persist=true) outputDir

#@ UIService uiService
#@ CommandService commandService
#@ DatasetService datasetService
#@ OpService ops
#@ DatasetIOService datasetioService
#@ IOService ioService
#@ ConvertService converter
#@ PlotService plotService

//region Utility functions

def ImgtoDataset(RandomAccessibleInterval input, Dataset Metadata){
    return datasetService.create(ImgPlus.wrap(
                    ImgView.wrap(input),
                    Metadata.getImgPlus()
            ));
}

//def opsMeshToCustomMesh(opsMesh, color) {
//    List<Point3f> points = new ArrayList<Point3f>();
//    for (t in opsMesh.triangles()) {
//        points.add(new Point3f(t.v0xf(), t.v0yf(), t.v0zf()));
//        points.add(new Point3f(t.v1xf(), t.v1yf(), t.v1zf()));
//        points.add(new Point3f(t.v2xf(), t.v2yf(), t.v2zf()));
//    }
//    CustomTriangleMesh ctm = new CustomTriangleMesh(points);
//    ctm.setColor(color);
//    return ctm;
//}
//
//def showMesh(Mesh inputMesh){	
//	def mesh3dv = opsMeshToCustomMesh(inputMesh, new Color3f(1, 0, 1));
//	
//	// Display original image and meshes in 3D Viewer.
//	def univ = new Image3DUniverse();
//	//univ.addVoltex(imp, 1)
//	univ.addCustomMesh(mesh3dv, "Surface Mesh");
//	univ.show();
//}

//endRegion

//region Main script start

STLMeshIO stlIO = new STLMeshIO();

Table concatenatedTable;

ArrayList<HashMap<String, Long>> runningTable = new ArrayList<>();
ArrayList<String> imageNames = new ArrayList<>();

File[] folderList = dicomDirectory.listFiles();

for (int i = 0; i < folderList.length; ++i) {
	if(!folderList[i].isDirectory()){
		continue;
	}
	println("Opening DICOM folder: " + folderList[i].getPath());
	
	ImagePlus inputImagePlus = FolderOpener.open(folderList[i].getPath(), "");
	Dataset image = converter.convert(inputImagePlus, net.imagej.Dataset.class);
	inputImagePlus.close();
	
	File imageOutput = new File(outputDir.getPath() + File.separator + image.getName() + File.separator);
	imageOutput.mkdirs();
	
	//Measure direct contact: 1. Segment each, clean, dilate both slightly, AND operation, measure volume.	
	//region Generate Masks
	
	double scale = image.axis(Axes.X).get().calibratedValue(1);
	
	double volume = image.axis(Axes.X).get().calibratedValue(1) * 
			image.axis(Axes.Y).get().calibratedValue(1) * 
			image.axis(Axes.Z).get().calibratedValue(1);
	
	Img boneMask = ops.create().img(image, new BitType());
	
	println("Generating bone mask");
	BiConsumer aboveBelow = {input,mask -> mask.set(input.get() > minImageBone && input.get() <maxImageBone)};	
	LoopBuilder.setImages(image, boneMask).multiThreaded().forEachPixel(aboveBelow);
	
	println("Cleaning bone mask");
	boneMask = ops.morphology().erode(boneMask, shape);
	boneMask = ops.morphology().dilate(boneMask, shape);
	
	println("Generating implant mask");
	ComplexType thresholdValue = image.getType();
	thresholdValue.set(minImplant);
	Img implantMask = ops.threshold().apply(image, thresholdValue);
	println("Converting implant mask to mesh and saving");
	stlIO.save(
		Meshes.removeDuplicateVertices(
			ops.geom().marchingCubes(implantMask)
			,1)
		, outputDir.getPath() + File.separator + image.getName() + File.separator + image.getName() + "_implant-surface.stl"
	);
	
	println("Calculating contact volume");
	Img contactMaskRegion = ops.morphology().dilate(implantMask, contactAreaShape);
	//endRegion Generate Masks

	double contactVolume = Regions.countTrue(ops.logic().and(boneMask, contactMaskRegion))*volume;
	
//	double contactVolume = Masks.toIterableRegion(
//		Masks.and(
//			Masks.toMaskInterval(boneMask),
//			Masks.toMaskInterval(contactMaskRegion)
//		)
//	).size()*volume;
	
	contactMaskRegion = null;
	
	
	//Generate and save STL files for both Bone and Implant
	
	//Measure bone within range of implant: 1. Segment each, clean, dilate, Convex Hull of bone, intersection(removes contribution of stuff from screw top), then dilate result extensively, measure bone in range. Normalize to intersection volume. Also report intersection volume
	//Need to clean up boneMask to be one solid object?
	println("Converting bone mask to surface mesh");
	Mesh hullSurface = ops.geom().marchingCubes(ops.morphology().fillHoles(boneMask));
	println("Removing duplicate verticies from surface mesh");
	hullSurface = Meshes.removeDuplicateVertices(hullSurface, 1);
	
	println("Saving bone mesh to STL");
	
	hullSurface = Meshes.simplify(hullSurface, 0.1, 10);
	stlIO.save(hullSurface, outputDir.getPath() + File.separator + image.getName() + File.separator + image.getName() + "_bone-surface.stl");
	
	println("Calculating convex hull");	
	hullSurface = (Mesh) ops.geom().convexHull(hullSurface).get(0);
	//stlIO.save(hullSurface, outputDir.getPath() + File.separator + image.getName() + File.separator + image.getName() + "_hull-surface.stl");
	
	println("Revoxelizing convex hull");
	//Ops voxelization sucks, going to try something else
	//Img hullImg = ops.geom().voxelization(hullSurface, (int)image.dimension(Axes.X), (int)image.dimension(Axes.Y), (int)image.dimension(Axes.Z));
	Img<BitType> hullMask = voxelizer(hullSurface, image);
	
	hullSurface = null;
	
	
	hullMask = ops.morphology().erode(ops.morphology().fillHoles(ops.morphology().dilate(hullMask, shape)),shape);
	
	implantMask = ops.logic().and(implantMask, hullMask);
	double embeddedSize = Regions.countTrue(implantMask)*volume;
	hullMask = null;
	
	implantMask = ops.morphology().dilate(implantMask, boneInRangeShape);
	
	double boneInRange = Regions.countTrue(ops.logic().and(implantMask, boneMask))*volume;
	
	imageNames.add(image.getName());
	runningTable.add(
		ImmutableMap.of(
			"Volume of contact patch: ", contactVolume,
			"Normalized volume of contact patch: ", contactVolume/embeddedSize,
			"Bone volume within range of " + (boneVolumeRange*scale) + " mm: ", boneInRange,
			"Normalized bone volume in range: ", boneInRange/embeddedSize,
			"Volume of screw embedded in bone: ", embeddedSize
		)
	);
	
	concatenatedTable = Tables.wrap(runningTable, imageNames);
	ioService.save(concatenatedTable, outputDir.getPath() + File.separator + "concatenatedTable.csv");
	
}


uiService.show(concatenatedTable);

println("All Finished: " + (java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)));

//voxelizer functions:

def Img<BitType> voxelizer(Mesh inputMesh, Interval dimensions){
	Img<BitType> outputImg = ops.create().img(dimensions, new BitType());
	net.imglib2.RandomAccess<BitType> ra = outputImg.randomAccess();
	Consumer triangleFunction = {Triangle t -> {
			Interval triangleBox = boundingBox(t);
			LongStream.rangeClosed(triangleBox.min(0), triangleBox.max(0)).parallel().forEach( x ->{
				LongStream.rangeClosed(triangleBox.min(1), triangleBox.max(1)).forEach( y ->{
					LongStream.rangeClosed(triangleBox.min(2), triangleBox.max(2)).forEach( z ->{
						if (pointToTriangleDist(new Vector3D((double)x, (double)y, (double)z), t) < 1.0){
							synchronized(ra){
								ra.setPositionAndGet(x,y,z).set(true);
							}
						}
					});
				});
			});
		}
	};	
	inputMesh.triangles().forEach(triangleFunction);
	
	return outputImg;
}

def Interval boundingBox(Triangle t){
	long [] min = new long[3];
	long [] max = new long[3];
	
	min[0] = Math.floor(Math.min(t.v0xf(), Math.min(t.v1xf(), t.v2xf())));
	min[1] = Math.floor(Math.min(t.v0yf(), Math.min(t.v1yf(), t.v2yf())));
	min[2] = Math.floor(Math.min(t.v0zf(), Math.min(t.v1zf(), t.v2zf())));
	
	max[0] = Math.ceil(Math.max(t.v0xf(), Math.max(t.v1xf(), t.v2xf())));
	max[1] = Math.ceil(Math.max(t.v0yf(), Math.max(t.v1yf(), t.v2yf())));
	max[2] = Math.ceil(Math.max(t.v0zf(), Math.max(t.v1zf(), t.v2zf())));
	
	return new FinalInterval(min, max);

}

def pointToTriangleDist(Vector3D p, Triangle t){
	Vector3D tPoint = nearestPointInTriangle3D(p, t);
	//println("Distance between point " + p.getX() + " , " + p.getY() + " , " + p.getZ() + " and nearest point " tPoint.getX() + " , " + tPoint.getY() + " , " + tPoint.getZ() + " is: " + p.distance(tPoint));
	return p.distance(tPoint);	
}



def Vector3D nearestPointInTriangle3D(Vector3D origP, Triangle t) {
	
	final Vector3D a = new Vector3D(t.v0x(), t.v0y(), t.v0z());
	final Vector3D b = new Vector3D(t.v1x(), t.v1y(), t.v1z());
	final Vector3D c = new Vector3D(t.v2x(), t.v2y(), t.v2z());		
	
	Vector3D ab = b.subtract(a); //prev v1
    Vector3D ac = c.subtract(a); //prev v0
	
	
	//region Obtain projection (p) of origP onto plane of triangle
    // Find the normal to the plane: n = (b - a) x (c - a)
    Vector3D n = ab.crossProduct(ac);

    // Normalize normal vector
    
    try{
    	n = n.normalize();
    }
    catch(Exception e){
    	println("Error: Degenerate triangle.");
        return  new Vector3D(-1,-1,-1);  // Triangle is degenerate
    } 

    //    Project point p onto the plane spanned by a->b and a->c.
    //
    //    Given a plane
    //
    //        a : point on plane
    //        n : *unit* normal to plane
    //
    //    Then the *signed* distance from point p to the plane
    //    (in the direction of the normal) is
    //
    //        dist = p . n - a . n
    //
    double dist = origP.dotProduct(n) - a.dotProduct(n);

    // Project p onto the plane by stepping the distance from p to the plane
    // in the direction opposite the normal: proj = p - dist * n
    Vector3D p = origP.add(n.scalarMultiply(-dist));

    // Compute edge vectors
    
    Vector3D ap = p.subtract(a); //prev vProj
    
    //endRegion
    
	//region nearest point is corners
	
	final double abDOTap = ab.dotProduct(ap);
	final double acDOTap = ac.dotProduct(ap);
	
	if (abDOTap <= 0d && acDOTap <= 0d) return a; 

	final Vector3D bc = c.subtract(b);
	final Vector3D bp = p.subtract(b);
	
	final double baDOTbp = ab.negate().dotProduct(bp);
	final double bcDOTbp = bc.dotProduct(bp);
	if (baDOTbp <= 0d && bcDOTbp <= 0d) return b; 
	
	
	final Vector3D cp = p.subtract(c);
	final double cbDOTcp = bc.negate().dotProduct(cp);
	final double caDOTcp = ac.negate().dotProduct(cp);
	if (cbDOTcp <= 0d && caDOTcp <= 0d) return c;
	//endregion
	
	//region nearest point is edge	
    

    // Compute dot products/rename variables to match original source
    double acDOTac = ac.dotProduct(ac); //prev dot00
    double abDOTac =  ab.dotProduct(ac); //prev dot01
    //double dot0proj = acDOTap; //prev dot0proj
    double abDOTab = ab.dotProduct(ab); //prev dot11
    //double dot1proj = abDOTap; //prev dot1proj

    // Compute barycentric coordinates (v, w) of projection point
    double denom = (acDOTac * abDOTab - abDOTac *abDOTac);
    if (Math.abs(denom) < 1.0e-30) {
    	println("Error: Degenerate triangle.");
    	return new Vector3D(-1,-1,-1); // Triangle is degenerate
    }
    
    double w = (acDOTac * abDOTap - abDOTac * acDOTap)/denom; //coordinate towards b from a
    double v = (abDOTab * acDOTap - abDOTac * abDOTap)/denom; //coordinate towards c from a


    // Check barycentric coordinates
    if ((v >= 0) && (w >= 0) && (v + w <= 1)) {
        // Nearest orthogonal projection point is in triangle
        return p;
    }
        
    if(w <= 0 && v > w){
	  	return a.add(ab.scalarMultiply(v));
    }
    
    if(v <= 0 && w > v){
	  	return a.add(ac.scalarMultiply(w));    	
    }
    
    if(v + w > 1){
   		final double scalarValue = bcDOTbp/bc.getNormSq();
		return b.add(bc.scalarMultiply(scalarValue));
    }
    
    if (v <=0 && w <= 0){ //this should be redundant, but for some reason isn't
    	return a;
    }
    
    println("Error: didn't match any condition.");
    return new Vector3D(-1,-1,-1);    
}
