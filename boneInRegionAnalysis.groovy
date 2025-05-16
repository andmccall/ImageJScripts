/**
 * Script to analyze the same masked region of multiple provided microCT scans
 * 
 * Will analyze:
 * - Bone Volume, Total Volume, and BV/TV
 * - Tissue density (density of isolated bone in Region), and Bone density (total density of region)
 * 
 * @author Andrew McCall
 */
 
import net.imagej.axis.Axes;
 
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import io.scif.config.SCIFIOConfig;
 
import org.scijava.table.Table;
import org.scijava.table.Tables;

import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.roi.*;
import net.imglib2.roi.labeling.*;
import net.imglib2.view.Views;

int thresholdValue= 38577; //38046 = 300 mgHA/ccm, 38577 = 350 mgHA/ccm
float calibrationSlope = 0.0941162;
float calibrationOffset = -3280.710007;


#@ File[] (label="Select registered microCT images", style = "files") fileList
#@ File (label="Provide analysis region mask", style = "file") maskFile
#@ File (label="Set output folder", style="directory") outputDir
#@ UIService uiService
#@ OpService ops
#@ DatasetService datasetService
#@ DatasetIOService datasetioService
#@ IOService ioService
#@ ConvertService converter


Table concatenatedTable;

ArrayList<HashMap<String, Long>> runningTable = new ArrayList<>();
ArrayList<String> imageNames = new ArrayList<>();

if(!datasetioService.canOpen(maskFile.getPath())){
	println("Error, provided mask is not an image file.");
	return;
}

mask = ops.convert().bit(datasetioService.open(maskFile.getPath()));
IterableRegion maskRegion = Regions.iterable(mask);

config = new SCIFIOConfig();
config.writerSetFailIfOverwriting(false);

for (int i = 0; i < fileList.length; ++i) {
	if(!datasetioService.canOpen(fileList[i].getPath())){
		println("Skipping non-image file: " + fileList[i].getPath());
		continue;
	}
	
	println("Opening image: " + fileList[i].getPath());
	image = datasetioService.open(fileList[i].getPath());
	
	double pixelVolume = image.axis(Axes.X).get().calibratedValue(1) * 
			image.axis(Axes.Y).get().calibratedValue(1) * 
			image.axis(Axes.Z).get().calibratedValue(1);
	
	sampledMask = Regions.sample(maskRegion, image);
	
	ComplexType threshold = image.getType();
	threshold.setReal(thresholdValue);
	
	IterableRegion boneRegion; //boneRegion to be analyzed
	
	maskInterval = Masks.toMaskInterval(mask);
	allBoneInMask = maskInterval.and(Masks.toMaskInterval(ops.threshold().apply(image, threshold)));
	
	println("Identifying connected components");
	ImgLabeling<Integer, BoolType> labeledBones = ops.labeling().cca(Masks.toIterableRegion(allBoneInMask), StructuringElement.EIGHT_CONNECTED);
	
	LabelRegions<BoolType> boneRegions = new LabelRegions(labeledBones);

	/*
	 * This next loop currently searches for the largest connected bone in the mask region. 
	 * This could be altered to work based on density, basically searching if any enamel is present in a 
	 * region and then removing that region from the list. This has the advantage of only removing the tooth,
	 * but would likely be much more time consuming.
	 */
	println("Isolating largest component");
    boneRegions.getExistingLabels().forEach(thisRegionLabel -> {
    	LabelRegion<BoolType> thisRegion = boneRegions.getLabelRegion(thisRegionLabel);
    	//Masks.toIterableRegion
    	//int size = Masks.toIterableRegion(maskInterval.and(Masks.toMaskInterval(thisRegion))).size();
    	//println(size);
    	if(boneRegion == null || thisRegion.size() > boneRegion.size()){
    		boneRegion = thisRegion;
    	}
    });
	
	println("Largest bone in mask region found (mm^3): " + boneRegion.size()*pixelVolume);
	//uiService.show(ops.logic().and(ops.threshold().apply(image, threshold), mask));
	
	sampledBone = Regions.sample(boneRegion, image);
	
	println("Saving bone region to file");
	datasetioService.save(datasetService.create(ops.convert().int8(Views.zeroMin(boneRegion))), outputDir.getPath() + File.separator + image.getName() + "-largestBoneRegion.tif", config);
	
	println("Analyzing bone in region");
	imageNames.add(image.getName());
	runningTable.add(
		ImmutableMap.of(
			"Bone Volume (mm^3)", (sampledBone.size()*pixelVolume),
			"Total Volume (mm^3)", (sampledMask.size()*pixelVolume),
			"BV/TV", (sampledBone.size()/sampledMask.size()),
			"Tissue Density (mgHA/ccm)", ((ops.stats().mean(sampledBone).get()*calibrationSlope) + calibrationOffset),
			"Bone Density (mgHA/ccm)", ((ops.stats().mean(sampledMask).get()*calibrationSlope) + calibrationOffset)
		)
	);
	
	println("Updating exported table");
	concatenatedTable = Tables.wrap(runningTable, imageNames);
	ioService.save(concatenatedTable, outputDir.getPath() + File.separator + "concatenatedTable.csv");
}

uiService.show(concatenatedTable);
