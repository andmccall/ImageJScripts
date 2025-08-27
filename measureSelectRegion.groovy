/**
 * Simple script that measures the mean and stDev of a single channel in an image. 
 * Designed to work with .lif files, so will open all series data of the input files.
 * Also allows users to pick from labeled images which labeled regions they will analyze.
 * Automatically saves output table, concatendatedTable.csv, to provided output folder. 
 *  
 * 
 * @param fileList List of image files to run
 * @param measureChannel The channel number to measure the mean and StDev (start from 1)
 * @param Output folder location for automatic table saving
 * 
 * @author Andrew McCall
 **/


//RegionSizeLimit (size limit of the region in cubic microns):
float minRegionSize = 2000;

//Gaussian sigma values in pixels
float dogLower = 2.0;
float dogUpper = 50.0;

//Using Moments threshold algorithm
//int thresholdValue = 3000;

import ij.IJ;
import ij.WindowManager;
import ij.gui.*;

import net.imglib2.img.Img;
import net.imagej.axis.Axes;
import net.imagej.Dataset;

import net.imglib2.IterableInterval;
import net.imglib2.view.Views;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.*;
import net.imglib2.roi.Regions;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.scijava.table.Table;
import org.scijava.table.Tables;

#@ File[] (label="Select images", style = "files") fileList
#@ Integer (label="Channel to measure", value=3, persist=true) measureChannel
#@ File (label="Table output directory", style="directory", persist=true) outputDir
#@ UIService uiService
#@ ModuleService moduleService
#@ OpService ops
#@ DatasetService datasetService
#@ DatasetIOService datasetioService
#@ IOService ioService
#@ ConvertService convertService

Table concatenatedTable;

ArrayList<HashMap<String, Long>> runningTable = new ArrayList<>();
ArrayList<String> cellNames = new ArrayList<>();

--measureChannel;

RectangleShape shape = new RectangleShape(3, true);

for (int i = 0; i < fileList.length; ++i) {
	if(!datasetioService.canOpen(fileList[i].getPath())){
		println("Skipping non-image file: " + fileList[i].getPath());
		continue;
	}

	println("Opening dataset " + fileList[i].getPath());
	IJ.run("Bio-Formats Importer", "open=["+fileList[i].getPath()+"] autoscale color_mode=Composite open_all_series rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	
	List<Dataset> imageList = new ArrayList();
	for (id in WindowManager.getIDList()) {
		imageList.add(convertService.convert(WindowManager.getImage(id),  net.imagej.Dataset.class));
	}
	
	for(Dataset thisDataset: imageList){
		float pixelVolume = thisDataset.axis(Axes.X).get().calibratedValue(1) * thisDataset.axis(Axes.Y).get().calibratedValue(1);
		channelImage = ops.transform().hyperSliceView(thisDataset, thisDataset.dimensionIndex(Axes.CHANNEL), measureChannel);
		
		println("Segmenting image");		
//		ComplexType threshold = thisDataset.getType();
//		threshold.setReal(thresholdValue);
		blurred = ops.filter().dog(ops.convert().float32(channelImage), dogUpper, dogLower);
//		uiService.show(blurred);
//		continue;
		binarized = ops.threshold().moments(blurred);
		
		
		//Watershed option
		println("Cleaning segmentation");
		binarized = ops.morphology().erode(binarized, shape);
		binarized = ops.morphology().dilate(binarized, shape);
		binarized = ops.morphology().dilate(binarized, shape);
		binarized = ops.morphology().erode(binarized, shape);
		ImgLabeling labeledImg = ops.labeling().cca(binarized, StructuringElement.EIGHT_CONNECTED);
		
		
		uiService.show(channelImage);
		uiService.show(labeledImg.getIndexImg());
		
		ArrayList<String> possibleLabels = new ArrayList();
		
		LabelRegions<BoolType> regions = new LabelRegions(labeledImg);
		
		regions.getExistingLabels().forEach(thisRegionLabel -> {
			LabelRegion<BoolType> thisRegion = regions.getLabelRegion(thisRegionLabel);
				float calibratedRegionSize = thisRegion.size()*pixelVolume;
				//println("Region size: " + calibratedRegionSize);
		        if(calibratedRegionSize >= minRegionSize) {
		        	possibleLabels.add((thisRegionLabel+1));
		        	return;
		        }
		});
		
		gui = GUI.newNonBlockingDialog("Select Regions");
		
		gui.addMessage("Select regions to analyze:");
		for(String label:possibleLabels){
			gui.addCheckbox(label, false);
		}
		
		gui.showDialog();
		
		ArrayList<Integer> keptLabels = new ArrayList();
		
		for(String label:possibleLabels){
			if(gui.getNextBoolean()){
				keptLabels.add((Integer.valueOf(label)-1));
			}
		}
		
		println("Analyzing channel data per label");
		for(Integer thisRegionLabel:keptLabels){
			LabelRegion<BoolType> thisRegion = regions.getLabelRegion(thisRegionLabel);
			IterableInterval labelChannelData = Regions.sample(thisRegion, channelImage);
			uiService.show(thisRegion);
	        
	        cellNames.add(thisDataset.getName() + "-" + (thisRegionLabel+1));
			runningTable.add(
				Maps.newHashMap(
					ImmutableMap.of(
						"Mean intensity", ops.stats().mean(labelChannelData),
						"St Dev", ops.stats().stdDev(labelChannelData)
					)
				)
			)
		}	
	}
	IJ.run("Close All", "");
	
	concatenatedTable = Tables.wrap(runningTable, cellNames);
	ioService.save(concatenatedTable, outputDir.getPath() + File.separator + "ConcatenatedTable.csv");
}
uiService.show(concatenatedTable);

println("All done!");
