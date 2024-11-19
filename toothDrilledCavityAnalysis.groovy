/**
 * @input: A µCT image of a tooth, with a point ROI marking the drill cavity.
 * Also requires a labkit classifier setup to seperate dentin and enamel into
 * the foreground, and real background (root and outside) as well as any filling 
 * material into the background pixels of the mask.
 * 
 * @output: A table of a line profile of local thicknesses perpindicular to
 * the plane of the narrowest constriction of the drill cavity. Also outputs
 * a mask image to be used to identify the plane and verify proper operation.
 * 
 * This script attempts to identify and measure a narrow constriction point in 
 * a µCT scan of a mouse (or rat) molar that has been drilled out down to the
 * root. To work, the drill hole must remain open, and the user must add
 * a point ROI within the cavity (should be near the middle, but doesn't have 
 * to be perfect). This point ROI is used to drop the 3D image around the
 * cavity, not for analysis.
 * 
 * The script performs a watershed from two seed points, one inside the tooth root
 * and one outside the tooth. The watershed convergence points, which should  
 * be inside the cavity, are used to  generate a best fit plane using singular 
 * value decomposition. A line profile is then taken from the midpoint of the plane
 * along the direction of the normal vector to the plane. Since the inside/outside
 * directionality cannot be established, the line profile may need to be flipped
 * in post-processing steps.
 * 
 * Occasionally, the convergence during the watershed is not confined well to the 
 * narrowest region of the drill hole. This is why the script outputs the watershed 
 * image, so that the user can quickly search it and verify the convergence looks
 * good. If not, adjust the crop size on execution of the script, as this seems to 
 * be the best fix for the watershed failure. 
 * 
 * @author Andrew McCall
 */


//Total distance of line profile, split evenly across midpoint of plane
float lineDistance = 0.4;
//
float lineReadIncr = 0.005;

//Old parameters for manual thresholding.
//float lowThresholdValue = 3500.0;
//float highThresholdValue = 9500;

import ij.IJ;
import ij.gui.Roi;
import ij.WindowManager;
import java.awt.Point;

import net.imagej.axis.Axes;

import net.imagej.ops.create.imgLabeling.CreateImgLabelingFromInterval;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;

import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.algorithm.stats.Max;
import net.imglib2.Cursor;
import net.imglib2.converter.RealTypeConverters;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.RandomAccess;
import net.imglib2.RealPoint;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.logic.BoolType;
import net.imglib2.roi.Masks;
import net.imglib2.roi.labeling.*;
import net.imglib2.roi.Regions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.Views;
import net.imglib2.img.basictypeaccess.FloatAccess;
import net.imglib2.view.composite.RealComposite;
import net.imglib2.img.display.imagej.ImageJFunctions;

import org.scijava.module.Module;
import org.scijava.module.ModuleInfo;
import org.scijava.table.Table;
import org.scijava.table.Tables;


#@ File (label="Dentin + Enamel classifier", style = "file") classifier
#@ Float (label="Crop half size. Min of 0.2", value = 0.4) cropHalfWidth
#@ Dataset input
#@ ImagePlus imp
#@ UIService uiService
#@ ConvertService converter
#@ OpService ops
#@ DatasetService dsService
#@ DatasetIOService datasetioService
#@ IOService ioService
#@ ModuleService moduleService


import java.util.Arrays;
import org.apache.commons.math3.linear.*;

public class GeometricPlaneFitting {
	
	private RealVector offset;
	private RealMatrix centeredCloud;
	private SingularValueDecomposition svd;
	
	
	public GeometricPlaneFitting(double [][] points){
		int n = points.size();
        println(n);
        if (n < 3) {
            throw new IllegalArgumentException("At least 3 points are required to fit a plane.");
        }

        // Create matrices for SVD
        //Each row is a point; columns are x, y, and z
		centeredCloud = new Array2DRowRealMatrix(points);
		offset = new ArrayRealVector(new double [] {
			Arrays.stream(centeredCloud.getColumn(0)).average().orElse(0),
			Arrays.stream(centeredCloud.getColumn(1)).average().orElse(0),
			Arrays.stream(centeredCloud.getColumn(2)).average().orElse(0)});
		
		for(int i=0; i < centeredCloud.getRowDimension(); ++i){
			centeredCloud.setRowVector(i, centeredCloud.getRowVector(i).subtract(offset));
		}
    	
    	svd = new SingularValueDecomposition(centeredCloud);
    	
    	
	}	
	
	public RealVector getOffset(){
		return offset;
	}
	
	public double[] getOffsetArray(){
		return offset.toArray();
	}
	
	public SingularValueDecomposition getSVD(){
		return svd;
	}
	
	public RealVector getNormal(){
		return new ArrayRealVector(svd.getV().getColumn(2));
	}

	public double [] getNormalArray(){
		return svd.getV().getColumn(2);
	}
//
//    public double[] getFitPlane() {    	
//
//        double[] normal = svd.getV().getColumn(2);
//
//        // Calculate D from the plane equation Ax + By + Cz + D = 0
//        double d = -normal[0] * offset[0] - normal[1] *offset[1];
//
//        return new double[]{normal[0], normal[1], normal[2], d};
//    }

}



def getObjectCount(RandomAccessibleInterval edtImage, ComplexType threshold){
	//numSets() includes background (0) as a label
	return ops.labeling().cca(
		ops.threshold().apply(edtImage, threshold),
		StructuringElement.EIGHT_CONNECTED
	).getMapping().numSets();
}

def getTwoSeedValue(RandomAccessibleInterval<FloatType> edtImage){
	int maxIter = 100;
	
	FloatType threshold = new FloatType();
	float currentThreshold = ops.stats().max(edtImage).get();
	float stepSize = currentThreshold/2;
	currentThreshold = currentThreshold - stepSize;	
	for(int iter = 0; iter < maxIter; ++iter){
		threshold.setReal(currentThreshold);
		long count = getObjectCount(edtImage, threshold);
		if(count < 3){
			stepSize = stepSize/2;
			currentThreshold = currentThreshold - stepSize;
		}
		//count of 3 means two non-background regions
		else if(count == 3)
			break;
		else if(count > 3){
			stepSize = stepSize/2;
			currentThreshold = currentThreshold + stepSize;
		}
		
	}
	return currentThreshold;	
}
def makeRai(IterableInterval ii, RandomAccessibleInterval target) {
    Cursor sampler = ii.localizingCursor();
    RandomAccess targetWriter = target.randomAccess();
    while(sampler.hasNext()){
    	sampler.fwd();
    	targetWriter.setPositionAndGet(sampler.positionAsLongArray()).set(sampler.get());
    }
}

def long[] findMaxPos(IterableInterval iterable){
	final Cursor cursor = iterable.localizingCursor();
		cursor.fwd();
		float max = cursor.get().get();
		long[] maxPos = cursor.positionAsLongArray();
		while ( cursor.hasNext() ){	
			if ( cursor.next().get().compareTo( max ) > 0){							
				max = cursor.get().get();
				maxPos = cursor.positionAsLongArray();
			}
		}
		return maxPos;
}

def RAItoDataset(RandomAccessibleInterval input){
    return dsService.create(
                ops.create().imgPlus(
                        ImgView.wrap(input)
                )
	       )	
}

def getNormalProfile(RealRandomAccessible image, RealVector offset, RealVector normal, float scale, float profileLength, float profileIncr){
	HashMap <String,Float> profileTable = new LinkedHashMap();
	float pixRange = (profileLength/scale)/2;
	RealRandomAccess accessor = image.realRandomAccess();
	for (double i = -pixRange; i <= pixRange; i += (profileIncr/scale)){
		RealVector position = offset.add(normal.mapMultiply(i));
		profileTable.put((""+(i*scale)), accessor.setPositionAndGet(position.toArray()).get()*scale);		
	}
	return profileTable;
}



//Main script start --------------

ModuleInfo labkit = moduleService.getModuleById("command:sc.fiji.labkit.ui.plugin.SegmentImageWithLabkitPlugin");

println("Script started");

ArrayImgFactory<IntType> intFactory = new ArrayImgFactory(new IntType());
float scale = input.axis(Axes.X).get().calibratedValue(1);
Point impRoi  = imp.getRoi().getContainedPoints()[0];
long [] givenPoint = new double[] {impRoi.getX(), impRoi.getY(), imp.getSlice()};
long[] min = new long[3];
long[] max = new long[3];

for(int d = 0; d < min.length; ++d){
	min[d] = Math.max(Math.round(givenPoint[d] - Math.round(cropHalfWidth/scale)), input.min(d));
	max[d] = Math.min(Math.round(givenPoint[d] + Math.round(cropHalfWidth/scale)), input.max(d));
	givenPoint[d] = givenPoint[d]-min[d];
}

FinalInterval cropInt = new FinalInterval(min, max);
cropped = Views.zeroMin(ops.transform().crop(input, cropInt));
//uiService.show(cropped);


/* Old manual thresholding code; worked okay, but labkit keeps enamel and works better
ComplexType threshold = input.getType();
//Generate mask of filling, to remove from mask above dentin threshold
threshold.setReal(highThresholdValue);
Img fillingMask = ops.threshold().apply(cropped, threshold);

fillingMask = ops.morphology().erode(fillingMask, new RectangleShape(1, true));
fillingMask = ops.morphology().dilate(fillingMask, new RectangleShape(5, true));
fillingMask = ops.morphology().fillHoles(fillingMask);
//uiService.show(fillingMask);

threshold.setReal(lowThresholdValue);
Img mask = ops.threshold().apply(cropped, threshold);
mask = ops.morphology().fillHoles(mask);

//ops.math.subtract does not seem to be zero-bounded for BitType, will need to use Loopbuilder; LoopBuilder is giving an argument mismatch error
Cursor filling = fillingMask.cursor();
Cursor dentin = mask.cursor();

while(filling.hasNext()){
	filling.fwd();
	dentin.fwd();
	if(filling.get().getInteger() != 0 && dentin.get().getInteger() != 0){
		dentin.get().setZero();
	}
}

Img<BitType> invertMask = mask.copy();
ops.image().invert(invertMask, mask);
//uiService.show(invertMask);
Img temp = invertMask.factory().create(invertMask);
*/


println("Segementing image with Labkit");
Img labkitMask = moduleService.run(labkit, false,
        "input", cropped,
        "segmenter_file", classifier
).get().getOutput("output");

ComplexType threshold = labkitMask.getType();
threshold.setReal(0);
Img mask = ops.threshold().apply(labkitMask, threshold);
RectangleShape shape = new RectangleShape(1, true);
mask = ops.morphology().erode(ops.morphology().dilate(mask,shape), shape);
shape = new RectangleShape(5, true);
mask = ops.morphology().dilate(ops.morphology().erode(mask,shape), shape);
mask = ops.morphology().fillHoles(mask);

Img<BitType> invertMask = mask.copy();
ops.image().invert(invertMask, mask);


Img temp = invertMask.factory().create(invertMask);
ImgLabeling initialStructures = ops.labeling().cca(invertMask,StructuringElement.EIGHT_CONNECTED);
LabelRegions<BoolType> initialRegions = new LabelRegions(initialStructures);
initialRegions.getExistingLabels().forEach(thisRegionLabel -> {
	LabelRegion<BoolType> thisRegion = initialRegions.getLabelRegion(thisRegionLabel);
	if(thisRegion.randomAccess().setPositionAndGet(givenPoint).get()){
		invertMask = makeRai(Regions.sample(thisRegion,invertMask), temp);
	}		
});

invertMask = ops.morphology().fillHoles(temp);
//uiService.show(invertMask);

println("Generating local thickness map");
//Generate Local Thickness; only part not headless compatible :(
impMask = converter.convert(RAItoDataset(invertMask), ij.ImagePlus.class);
IJ.run(impMask, "Local Thickness (complete process)", "threshold=128");
locThicknessImagePlus = WindowManager.getImage("_LocThk");
locThickness = converter.convert(locThicknessImagePlus,net.imagej.Dataset.class);

println("Generating EDT and calculating seed values");
edt = ops.image().distancetransform(invertMask);
//uiService.show(edt);
float twoObjThresh = getTwoSeedValue(edt);
println("EDT image two object threshold: " + twoObjThresh);

//uiService.show(ops.threshold().apply(edt, threshold));

ComplexType edtThreshold = new FloatType();
edtThreshold.setReal(twoObjThresh);
ImgLabeling twoRegionLabeling = ops.labeling().cca(
	ops.threshold().apply(edt, edtThreshold),
	StructuringElement.EIGHT_CONNECTED
);
//uiService.show(twoRegionLabeling.getIndexImg());

Img seeds = intFactory.create(edt);

//println(findMax(Views.iterable(edt)).positionAsLongArray());
//uiService.show(seeds);


RandomAccess seedsAccessor = seeds.randomAccess();
List<Integer> labelList = new ArrayList();
LabelRegions<BoolType> twoRegions = new LabelRegions(twoRegionLabeling);

twoRegions.getExistingLabels().forEach(thisRegionLabel -> {
	LabelRegion<BoolType> thisRegion = twoRegions.getLabelRegion(thisRegionLabel);
	long[] maxCursor = findMaxPos(Regions.sample(thisRegion, edt));
	seedsAccessor.setPositionAndGet(maxCursor).set(new IntType(thisRegionLabel+1));
	labelList.add(thisRegionLabel+1);
});



ImgLabeling<Integer, IntType> seedLabeling = ImgLabeling.fromImageAndLabels(seeds, labelList);
println("Performing watershed");
ImgLabeling watershed = ops.image().watershed(null, invertMask, seedLabeling, true, true, invertMask);
uiService.show(watershed.getIndexImg());

LabelRegions<BoolType> watershedRegions = new LabelRegions(watershed);
LabelRegion<BoolType> watershedLine = watershedRegions.getLabelRegion(-1);

double [][] planePoints = new double [watershedLine.size()][3]; 

int i = 0;
Cursor lineCursor = watershedLine.cursor();
while(lineCursor.hasNext()){
	lineCursor.fwd();
	planePoints[i++] = lineCursor.positionAsDoubleArray();
}
println("Fitting plane to watershed border");
GeometricPlaneFitting planeFitter = new GeometricPlaneFitting(planePoints);


//uiService.show(locThickness);

RealRandomAccessible realLocThickness = Views.interpolate(Views.extendZero(locThickness), new NLinearInterpolatorFactory());

println("Generating table");
LinkedHashMap profile = getNormalProfile(realLocThickness, planeFitter.getOffset(), planeFitter.getNormal(), scale, lineDistance, lineReadIncr);

Table profileTable = Tables.wrap(profile, "Thickness");

uiService.show(profileTable);


locThicknessImagePlus.close();
println("Script finished");


//Code to get and view hessian filters:
//CompositeIntervalView hessian = ops.filter().hessian(edt);
//
//for(int imgIdx=0; imgIdx < 3; ++imgIdx){
//	RandomAccessibleInterval filterOut = net.imglib2.converter.Converters.convert(hessian, in -> new FloatType(new FloatAccess() {
//		@Override
//		public float getValue(int index) {
//			return in.get().get(imgIdx).get();
//		}
//
//		@Override
//		public void setValue(int index, float value) {
//			in.get().get(imgIdx).set(value);
//		}
//	}));
//	uiService.show(filterOut);
//}
