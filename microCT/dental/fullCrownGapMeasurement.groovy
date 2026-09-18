/* This script analyzes the gap thickness between inserts and crowns in microCT images.
 *  All provided images and analysis region masks must have the same dimensions.
 *  
 *  Also requires a LabKit classifier that separates background from the implant and crown 
 *  (one object), and the cement.
 * 
 * @author Andrew McCall
 */

import org.scijava.table.Table;
import org.scijava.table.Tables;
import org.scijava.command.CommandModule;

import net.imagej.axis.Axes;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
 
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import net.imglib2.algorithm.binary.Thresholder;
import java.util.function.*;
import java.util.stream.LongStream;

import io.scif.config.SCIFIOConfig;

import net.imglib2.algorithm.neighborhood.*;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.util.Intervals;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.*;
import net.imglib2.roi.mask.integer.RandomAccessibleIntervalAsMaskInterval;
import net.imglib2.roi.Masks;
import net.imglib2.roi.Regions;
import net.imglib2.Point;
import net.imglib2.roi.labeling.*;
import net.imglib2.type.logic.*;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.roi.util.IterableRegionOnBooleanRAI;

import net.imglib2.view.Views;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;

import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;
import customnode.CustomTriangleMesh;
import net.imagej.mesh.*;
import net.imagej.mesh.io.stl.STLMeshIO;
import ij3d.Image3DUniverse;


#@ File[] (label="Select registered microCT images", style = "files") fileList
#@ File[] (label="Provide masks of all analysis regions", style = "file") maskFiles
#@ File (label="Provide classifier file", style = "file") classifier
#@ boolean (label="Use GPU? (requires NVIDIA gpu and CLIJ)") useGPU
#@ File (label="Set output folder", style="directory") outputDir
#@ UIService uiService
#@ OpService ops
#@ OpEnvironment sciOps
#@ DatasetService datasetService
#@ DatasetIOService datasetioService
#@ IOService ioService
#@ CommandService commandService
#@ ConvertService converter


def ImgtoDataset(Img input, ImgPlus Metadata){
    return datasetService.create(
                ops.create().imgPlus(
                       input,
                        Metadata
                )
	       )	
}

def void copyLabelRegion2Img(Img<BitType> outputImg, IterableRegion region){
		Cursor cursor = region.localizingCursor();
		RandomAccess<BitType> ra = outputImg.randomAccess();
		
		while(cursor.hasNext()){
			cursor.fwd();
			if(cursor.get().get())
				ra.setPositionAndGet(cursor.positionAsPoint()).setOne();
		}
}



shape = new RectangleShape(3, true);

Table concatenatedTable;

ArrayList<HashMap<String, Long>> runningTable = new ArrayList<>();
ArrayList<String> imageNames = new ArrayList<>();

Dataset[] masks = new Dataset[maskFiles.length];

for (int i = 0; i < maskFiles.length; ++i) {
	if(!datasetioService.canOpen(maskFiles[i].getPath())){
		println("Error, provided mask is not an image file: " + maskFiles[i].getPath());
		return;
	}
	masks[i] = datasetioService.open(maskFiles[i].getPath());
}



for (int i = 0; i < fileList.length; ++i) {
	if(!datasetioService.canOpen(fileList[i].getPath())){
		println("Skipping non-image file: " + fileList[i].getPath());
		continue;
	}
	
	println("Opening image: " + fileList[i].getPath());
	Dataset image = datasetioService.open(fileList[i].getPath());
	
	float scale = image.axis(Axes.X).get().calibratedValue(1);

	println("Segmenting image");
	ImgLabeling labeledImage = ImgLabeling.fromImageAndLabels(commandService.run("sc.fiji.labkit.ui.plugin.SegmentImageWithLabkitPlugin", false,
		"input", image,
		"segmenter_file", classifier,
		"use_gpu", useGPU
	).get().getOutput("output"), new ArrayList(["teeth","cement"]));
	
	LabelRegions materialSelector = new LabelRegions(labeledImage);
	
	//	uiService.show(materialSelector.getLabelRegion("crown"));
	//	uiService.show(materialSelector.getLabelRegion("cement"));
	
	println("Performing connected-component analysis on cement");
	LabelRegions<BoolType> cementRegions = new LabelRegions(ops.labeling().cca(materialSelector.getLabelRegion("cement"), StructuringElement.EIGHT_CONNECTED));
	IterableRegion largestCementChunk; 
	
	//Find Largest cement object, dilate, marching cubes, convex hull, voxelize, use result as mask for negative space analysis
	
	println("Isolating largest cement component");
    cementRegions.getExistingLabels().forEach(thisRegionLabel -> {
    	LabelRegion<BoolType> thisRegion = cementRegions.getLabelRegion(thisRegionLabel);
    	//Masks.toIterableRegion
    	//int size = Masks.toIterableRegion(maskInterval.and(Masks.toMaskInterval(thisRegion))).size();
    	//println(size);
    	if(largestCementChunk == null || thisRegion.size() > largestCementChunk.size()){
    		largestCementChunk = thisRegion;
    	}
    });
	
    println("Dilating cement to buffer analysis region");
    Img<BitType> dilatedCement = ops.create().img(masks[0], new BitType());
    copyLabelRegion2Img(dilatedCement,largestCementChunk);	
	dilatedCement = ops.morphology().dilate(dilatedCement, shape);	
	println("Calculating convex hull of cement");
	Mesh cementSurface = ops.geom().marchingCubes(dilatedCement);	
	cementSurface = (Mesh) ops.geom().convexHull(cementSurface).get(0);
	Img<BitType> filledCement = ops.create().img(masks[0], new BitType());
	Img<BitType> cementMask = voxelizer(cementSurface, dilatedCement);
	sciOps.op("morphology.fillHoles").input(cementMask, new DiamondShape(1)).output(filledCement).compute();

	println("Finding gap between crown and implant");
	Img<BitType> binaryCrown = ops.create().img(masks[0], new BitType());	
	copyLabelRegion2Img(binaryCrown, materialSelector.getLabelRegion("teeth"));
//	uiService.show(binaryCrown);
	
	Img<BitType> gap = ops.create().img(masks[0], new BitType());

	binaryCrown = ops.morphology().erode(binaryCrown, shape);	
	binaryCrown = ops.morphology().dilate(binaryCrown, shape);

	sciOps.op("image.invert").input(binaryCrown).output(gap).compute();
//	uiService.show(gap);
	println("Masking gaps with Cement region");
	Img<BitType> confinedGapRegion = ops.create().img(masks[0], new BitType());
    sciOps.op("math.and").input(gap, filledCement).output(confinedGapRegion).compute();
    
    
	LabelRegions<BoolType> gapRegions = new LabelRegions(ops.labeling().cca(confinedGapRegion, StructuringElement.EIGHT_CONNECTED));
	IterableRegion largestGapRegion; 
	
	//Find Largest cement object, dilate, marching cubes, convex hull, voxelize, use result as mask for negative space analysis
	
	println("Isolating largest gap");
    gapRegions.getExistingLabels().forEach(thisRegionLabel -> {
    	LabelRegion<BoolType> thisRegion = gapRegions.getLabelRegion(thisRegionLabel);
    	if(largestGapRegion == null || thisRegion.size() > largestGapRegion.size()){
    		largestGapRegion = thisRegion;
    	}
    });
    
    Img<BitType> largestGap = ops.create().img(masks[0], new BitType());
    

    //IterableRegion confinedGapRegion = Masks.toIterableRegion(Masks.toMaskInterval(largestGapRegion).and(Masks.toMaskInterval(filledCement)));
    copyLabelRegion2Img(largestGap, largestGapRegion);
    
    
//    uiService.show(largestGap);
    
//    uiService.show(ImgtoDataset(largestGap,masks[0].getImgPlus()));
	
	println("Generating thickness map");
	ImagePlus largestGapImp = converter.convert(ImgtoDataset(largestGap, image.getImgPlus()), ij.ImagePlus.class);
	IJ.run((ImagePlus)largestGapImp, "Geometry to Distance Map", "threshold=128");
	IJ.run(WindowManager.getCurrentImage(), "Distance Map to Distance Ridge", "");
	Dataset ridgeImage = converter.convert(WindowManager.getCurrentImage(), net.imagej.Dataset.class);
	
	
	for(int j=0; j<masks.length; ++j){	
		println("Analyzing thickness of mask " + masks[j].getName());
		ComplexType threshold = masks[j].getType();
		threshold.setReal(0.5);
		Img<BitType> binaryMask = ops.threshold().apply(masks[j], threshold);
//		uiService.show(binaryMask);
		
		threshold = ridgeImage.getType();
		threshold.setReal(1);
		
		thicknessSampler = Regions.sampleWithMask(Masks.toMaskInterval(binaryMask).and(Masks.toMaskInterval(ops.threshold().apply(ridgeImage, threshold))), ridgeImage);
		//println("Should be non-zero: " + ops.stats().min(thicknessSampler));
		
		imageNames.add(masks[j].getName() + "-" + image.getName());
		runningTable.add(
			ImmutableMap.of("Thickness - Mean (mm)", ops.stats().mean(thicknessSampler).get()*scale,
				"Thickness - StDev (mm)", ops.stats().stdDev(thicknessSampler).get()*scale,
				"Thickness - Max (mm)", ops.stats().max(thicknessSampler).get()*scale
			)
		);
	}
	
	for(String openImageName:WindowManager.getImageTitles()){
		WindowManager.getImage(openImageName).changes = false;
		WindowManager.getImage(openImageName).close();
	}
	
	concatenatedTable = Tables.wrap(runningTable, imageNames);
	ioService.save(concatenatedTable, outputDir.getPath() + File.separator +"ConcatenatedTable.csv");	
}

uiService.show(concatenatedTable);


//voxelizer functions:

def Img<BitType> voxelizer(Mesh inputMesh, Interval dimensions){
	Img<BitType> outputImg = ops.create().img(dimensions, new BitType());
	net.imglib2.RandomAccess<BitType> ra = outputImg.randomAccess();
	Consumer triangleFunction = {Triangle t -> {
			Interval triangleBox = boundingBox(t);
			LongStream.rangeClosed(triangleBox.min(0), triangleBox.max(0)).parallel().forEach( x ->{
				LongStream.rangeClosed(triangleBox.min(1), triangleBox.max(1)).forEach( y ->{
					LongStream.rangeClosed(triangleBox.min(2), triangleBox.max(2)).forEach( z ->{
						if(Intervals.contains(dimensions, new Point(x,y,z))){
							if (pointToTriangleDist(new Vector3D((double)x, (double)y, (double)z), t) < 1.0){
								synchronized(ra){
									ra.setPositionAndGet(x,y,z).set(true);
								}
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
