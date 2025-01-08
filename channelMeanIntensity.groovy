/**
 * Simple script that measures the mean and stDev of a single channel for each cell. Cells are based off
 * provided LabKit pixel classifier. Automatically saves output table, concatendatedTable.csv, to provided output folder. 
 *  
 * Requires LabKit.
 * 
 * @param fileList List of image files to run
 * @param measureChannel The channel number to measure the mean and StDev (start from 1)
 * @param sigma The scaled sigma value for the Gaussian used in Watersheding
 * @param classifier A Labkit classifier to segment the original image
 * @param Output folder location for automatic table saving
 * 
 * @author Andrew McCall
 **/


//RegionSizeLimit (size limit of the full cell in cubic microns):
float minRegionSize = 200;
float maxRegionSize = 1500;

import net.imglib2.img.Img;
import net.imagej.axis.Axes;
import net.imagej.Dataset;

import net.imglib2.IterableInterval;
import net.imglib2.view.Views;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.type.logic.BoolType;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.*;
import net.imglib2.roi.Regions;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.scijava.module.ModuleInfo;
import org.scijava.table.Table;
import org.scijava.table.Tables;

#@ File[] (label="Select images", style = "files") fileList
#@ Integer (label="Channel to measure", value=2, persist=true) measureChannel
#@ Double (label="Scaled Gaussian sigma for watershed", value=1.5, persist=true) sigma
#@ File (label="Select classifier", style = "file") classifier
#@ boolean (label="Use GPU? (requires NVIDIA and CLIJ)", persist=true) useGPU
#@ File (label="Table output directory", style="directory", persist=true) outputDir
#@ UIService uiService
#@ ModuleService moduleService
#@ OpService ops
#@ DatasetIOService datasetioService
#@ IOService ioService

Table concatenatedTable;

ArrayList<HashMap<String, Long>> runningTable = new ArrayList<>();
ArrayList<String> cellNames = new ArrayList<>();

--measureChannel;

ModuleInfo labkit = moduleService.getModuleById("command:sc.fiji.labkit.ui.plugin.SegmentImageWithLabkitPlugin");

RectangleShape shape = new RectangleShape(3, true);

for (int i = 0; i < fileList.length; ++i) {
	if(!datasetioService.canOpen(fileList[i].getPath())){
		println("Skipping non-image file: " + fileList[i].getPath());
		continue;
	}

	println("Opening image " + fileList[i].getPath());
	image = datasetioService.open(fileList[i].getPath());
	
	final double[] sigmas = [sigma/image.axis(Axes.X).get().calibratedValue(1), sigma/image.axis(Axes.Y).get().calibratedValue(1), sigma/image.axis(Axes.Z).get().calibratedValue(1)];	
	float pixelVolume = image.axis(Axes.X).get().calibratedValue(1) * image.axis(Axes.Y).get().calibratedValue(1) * image.axis(Axes.Z).get().calibratedValue(1);
	channelImage = ops.transform().hyperSliceView(image, image.dimensionIndex(Axes.CHANNEL), measureChannel);
	
	println("Segmenting image");
	Dataset segmentedImage = moduleService.run(labkit, false,
		"input", image,
		"segmenter_file", classifier,
		"use_gpu", useGPU
	).get().getOutput("output");
	
	
	//Watershed option
	println("Watersheding image");
	Img binarized = ops.convert().bit(segmentedImage);
	binarized = ops.morphology().fillHoles(binarized);
	binarized = ops.morphology().erode(binarized, shape);
	binarized = ops.morphology().dilate(binarized, shape);
	binarized = ops.morphology().dilate(binarized, shape);
	binarized = ops.morphology().erode(binarized, shape);
	ImgLabeling labeledImg = ops.image().watershed(null, binarized, true, false, sigmas, binarized);
	//uiService.show(labeledImg.getIndexImg());
	ioService.save(labeledImg.getIndexImg(), fileList[i].getPath() + "-segementedImg.tif");
	
	//No watershed
	//ImgLabeling labeledImg = ops.labeling().cca(segmentedImage, StructuringElement.EIGHT_CONNECTED);
	
	LabelRegions<BoolType> regions = new LabelRegions(labeledImg);

	println("Analyzing channel data per cell");
	regions.getExistingLabels().forEach(thisRegionLabel -> {
		LabelRegion<BoolType> thisRegion = regions.getLabelRegion(thisRegionLabel);
		float calibratedRegionSize = thisRegion.size()*pixelVolume;
		//println("Region size: " + calibratedRegionSize);
        if(calibratedRegionSize <= minRegionSize || calibratedRegionSize >= maxRegionSize) {
        	println("Region size not within range: " + calibratedRegionSize);
        	return;
        }
        IterableInterval cellChannelData = Regions.sample(thisRegion, channelImage);
        
        cellNames.add(image.getName() + "-" + thisRegionLabel);
		runningTable.add(
			Maps.newHashMap(
				ImmutableMap.of(
					"Mean intensity", ops.stats().mean(cellChannelData),
					"St Dev", ops.stats().stdDev(cellChannelData)
				)
			)
		)
	});
}
concatenatedTable = Tables.wrap(runningTable, cellNames);

uiService.show(concatenatedTable);
ioService.save(concatenatedTable, outputDir.getPath() + File.separator + "ConcatenatedTable.csv");

println("All done!");
//ioService.save(concatenatedTable, folder.getPath() + File.separator + "concatenatedTable.csv");
