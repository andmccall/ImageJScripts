/**
 * This script separates GFP positive from GFP negative cells in loaded images, and separately analyzes 
 * autocorrelation and cross-correlation across multiple channel combinations using CCC. It then
 * concatenates all the results from an image together into one table and saves all the results.
 * This was designed to quantify puncta formation in the GFP positive nuclei.
 * 
 * Requires Colocalization by cross-correlation, and Labkit.  
 * Optionally requires CLIJ, and CLIJ2 with NVIDIA card to speed up processing.
 * 
 * The required LabKit classifier should be made to work on a two channel image, and produce two labels.
 * Details for LabKit classifier:
 * Channel 1: DAPI
 * Channel 2: GFP
 * 
 * Label 1: GFP negative cells
 * Label 2: GFP positive cells
 * 
 * @author Andrew McCall
 */

//Minimum size of gfpPos cell, in cubic microns, for cell to be analyzed
float minVolume = 50.0

//Script could be altered to re-scale image to target isometric pixel size; not currently used
//float labkitPixelTarget = 0.1;

 
import ij.IJ;
import ij.WindowManager;
import net.imagej.axis.Axes;
import net.imagej.Dataset;
import net.imagej.ImgPlus;

import io.scif.config.SCIFIOConfig;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.img.ImgView;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.*;
import net.imglib2.roi.Regions;
import net.imglib2.type.logic.BoolType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.scijava.module.ModuleInfo;
import org.scijava.table.Table;
import org.scijava.table.Tables;

#@ File (label="2 part Classifier") classifier
#@ Integer (label="DAPI Channel", value=1, persist=true) dapi
#@ Integer (label="E1 Channel", value=2, persist=true) gfp
#@ boolean (label="NVIDIA card and CLIJ2 installed?") useGPU
#@ ModuleService moduleService
#@ DatasetIOService datasetIOservice
#@ DatasetService datasetService
#@ IOService ioService
#@ UIService uiService
#@ OpService ops
#@ ConvertService convertService


def RAItoDataset(RandomAccessibleInterval input, ImgPlus Metadata){
    return datasetService.create(
                ImgPlus.wrap(
                        (Img)ImgView.wrap(input),
                        Metadata
                )
	       )	
}

def trimToRegion(Dataset input, Interval region){
	return RAItoDataset(
		Views.zeroMin(ops.transform().intervalView(input, region)),
		input.getImgPlus()
	)	
}

def addResultsToRunning(ArrayList<HashMap<String, Long>> runningTable, Table results){
	runningTable.add(
            Maps.newHashMap(
                    ImmutableMap.of("Mean", results.get(0,0),
                            "StDev", results.get(0,1),
                            "Confidence", results.get(0,2),
                            "R-Squared", results.get(0,3),
                            "Gaussian Height", results.get(0,4)
                    )
            )
    );
    
    return;
}

--gfp;
--dapi;

println("Found " + WindowManager.getIDList().length + " open images.");
List<Dataset> imageList = new ArrayList();
for (id in WindowManager.getIDList()) {
	imageList.add(convertService.convert(WindowManager.getImage(id),  net.imagej.Dataset.class));
}

//ArrayList<HashMap<String, Long>> runningTable = new ArrayList<>();
//ArrayList<String> imageNames = new ArrayList<>();

//List<Dataset> imageList = datasetService.getDatasets();
ModuleInfo ccc = moduleService.getModuleById("command:CCC.Colocalization_by_Cross_Correlation");
ModuleInfo labkit = moduleService.getModuleById("command:sc.fiji.labkit.ui.plugin.SegmentImageWithLabkitPlugin");

//println(imageList);

config = new SCIFIOConfig();
config.writerSetFailIfOverwriting(false);

for(Dataset thisDataset: imageList){
	
	ArrayList<HashMap<String, Long>> runningTableOfCorrelations = new ArrayList<>();
	ArrayList<String> correlationNameList = new ArrayList<>();
	
	int offset = thisDataset.getName().lastIndexOf(" - ") == -1 ? 0 : thisDataset.getName().lastIndexOf(" - ") + 3;
	thisDataset.setName(thisDataset.getName().substring(offset).replaceAll("/", " "));
	
	String rootSave = new String(thisDataset.getSource().substring(0, thisDataset.getSource().lastIndexOf(".")) + File.separator + thisDataset.getName() + File.separator);
	println(rootSave);
	new File(rootSave).mkdirs();
	
	float pixelVolume = thisDataset.axis(Axes.X).get().calibratedValue(1) * thisDataset.axis(Axes.Y).get().calibratedValue(1) * thisDataset.axis(Axes.Z).get().calibratedValue(1);
	long minPixels = Math.round(minVolume/pixelVolume);

	int channelAxis = thisDataset.dimensionIndex(Axes.CHANNEL);
	
	long[] min = new long[thisDataset.numDimensions()];
    long[] max = new long[thisDataset.numDimensions()];
    for(int d = 0; d < min.length; ++d){
        min[d] = thisDataset.min(d);
        max[d] = thisDataset.max(d);
    }
    min[channelAxis] = dapi;
    max[channelAxis] = dapi;    
    FinalInterval dapiInterval = new FinalInterval(min, max);
    dapiCh = ops.transform().crop(thisDataset, dapiInterval, false);
    
    min[channelAxis] = gfp;
    max[channelAxis] = gfp;    
    FinalInterval gfpInterval = new FinalInterval(min, max);
    gfpChImgPlus = ops.transform().crop(thisDataset, gfpInterval, false);
    
    Dataset labkitImage = datasetService.create(
		ops.create().imgPlus(
			ImgView.wrap(Views.concatenate(channelAxis, dapiCh, gfpChImgPlus)), thisDataset
		)
	)    
	
	Dataset gfpCh = datasetService.create(ops.transform().crop(thisDataset, gfpInterval, true));
    
    //RAItoDataset(Views.dropSingletonDimensions(dapiCh), thisDataset);
    //RAItoDataset(Views.dropSingletonDimensions(gfpCh), thisDataset);
    
    //Dataset[] otherChannels = new Dataset[thisDataset.dimension(Axes.CHANNEL)-2];
	//int i = 0;
	
	HashMap <int, Dataset> otherChannels = new HashMap();	

	for (int currentCh = 0; currentCh < thisDataset.dimension(Axes.CHANNEL); currentCh++) {
		if(currentCh == dapi || currentCh == gfp){
			continue;
		}		
		min[channelAxis] = currentCh;
	    max[channelAxis] = currentCh;    
	    FinalInterval currentChInterval = new FinalInterval(min, max);
	    otherChannels.put(currentCh+1, datasetService.create(ops.transform().crop(thisDataset, currentChInterval, true)));
	}
	
	int[] otherChannelsKeys = otherChannels.keySet().toArray();
	
	println("Segmenting " + thisDataset.getName());
	ImgLabeling labeledImage = ImgLabeling.fromImageAndLabels(moduleService.run(labkit, false,
		"input", labkitImage,
		"segmenter_file", classifier,
		"use_gpu", useGPU
	).get().getOutput("output"), new ArrayList(["gfpNegative","gfpPositive"]));
	
	//println(labeledImage.getMapping().numSets());
	LabelRegions gfpStatus = new LabelRegions(labeledImage);
	
//	uiService.show(gfpStatus.getLabelRegion("gfpNegative"));
//	uiService.show(gfpStatus.getLabelRegion("gfpPositive"));

	//region GFP Positive
	//need to analyze each cell individually, so CCA
	
	println("Performing connected-component analysis on GFP positive cells");
	ImgLabeling<Integer, BoolType> gfpPosCellLabels = ops.labeling().cca(gfpStatus.getLabelRegion("gfpPositive"), StructuringElement.EIGHT_CONNECTED);
	
	config.put("WRITE_BIG_TIFF", true);
	datasetIOservice.save(RAItoDataset(Views.zeroMin(gfpPosCellLabels.getIndexImg()), gfpCh.getImgPlus()), rootSave + thisDataset.getName() + "-labeledGfpPosCells.tif", config);
	config.put("WRITE_BIG_TIFF", false);
	
	LabelRegions<BoolType> gfpPosCellRegions = new LabelRegions(gfpPosCellLabels);
	
    gfpPosCellRegions.getExistingLabels().forEach(thisRegionLabel -> {
	    LabelRegion<BoolType> thisRegion = gfpPosCellRegions.getLabelRegion(thisRegionLabel);
	    
	    if(thisRegion.size() <=  minPixels){
	    	//skip marching cubes when even the region is too small
	    	return;
	    }
	    
	    if(ops.geom().size(ops.geom().marchingCubes(thisRegion)).get() <= minPixels) {
	    	return;
	    }
	    
	    println("Starting GFP pos cell labeled: " + (thisRegionLabel+1));	    
	    		
		//GFP autocorrelation
		println("Analyzing GFP Positive autocorrelation of E1");
		Table tempResultsTable = moduleService.run(ccc, false,
	        "dataset1", trimToRegion(gfpCh, thisRegion),
	        "dataset2", trimToRegion(gfpCh, thisRegion),
	        "maskAbsent", false,
	        "maskDataset", RAItoDataset(thisRegion, gfpCh.getImgPlus()),
	        "significantDigits", 4,
	        "generateContributionImages",true,
	        "showIntermediates",false ,
	        "saveFolder", new File(rootSave + "GFPpos" + File.separator + (thisRegionLabel+1) + File.separator + "E1xE1" + File.separator )
	    	).get().getOutput("resultsTable");
	    	
	    correlationNameList.add("GFPPos cell " + (thisRegionLabel+1) + " - E1xE1");
	    addResultsToRunning(runningTableOfCorrelations, tempResultsTable);
	    
//		HashMap<int, Table> gfpPosACResults = new HashMap<int, Table>();
//		HashMap<int, Table> gfpPosCCResults = new HashMap<int, Table>();
		otherChannels.forEach((chNum, chImage) ->
		{
			println("Analyzing GFP Positive crosscorrelation of E1xCh" + chNum);
		    tempResultsTable = moduleService.run(ccc, false,
		        "dataset1", trimToRegion(gfpCh, thisRegion),
		        "dataset2", trimToRegion(chImage, thisRegion),
		        "maskAbsent", false,
		        "maskDataset", RAItoDataset(thisRegion, chImage.getImgPlus()),
		        "significantDigits", 4,
		        "generateContributionImages",true,
		        "showIntermediates",false ,
		        "saveFolder", new File(rootSave + "GFPpos" + File.separator +(thisRegionLabel+1) + File.separator + "E1xCh" + chNum + File.separator)
		    	).get().getOutput("resultsTable");
		    	
		    correlationNameList.add("GFPPos cell " + (thisRegionLabel+1) + " - E1xCh" + chNum);
	    	addResultsToRunning(runningTableOfCorrelations, tempResultsTable);	
			
			println("Analyzing GFP Positive autocorrelation of Ch" + chNum);
		    tempResultsTable = moduleService.run(ccc, false,
		        "dataset1", trimToRegion(chImage, thisRegion),
		        "dataset2", trimToRegion(chImage, thisRegion),
		        "maskAbsent", false,
		        "maskDataset", RAItoDataset(thisRegion, chImage.getImgPlus()),
		        "significantDigits", 4,
		        "generateContributionImages",true,
		        "showIntermediates",false ,
		        "saveFolder", new File(rootSave + "GFPpos" + File.separator + (thisRegionLabel+1) + File.separator + "Ch" + chNum + File.separator)
		    	).get().getOutput("resultsTable");
		    	
		   	correlationNameList.add("GFPPos cell " + (thisRegionLabel+1) + " - Ch" + chNum + "xCh" + chNum);
	    	addResultsToRunning(runningTableOfCorrelations, tempResultsTable); 
		});
		
		if(otherChannelsKeys.length == 2){
			println("Analyzing GFP Positive crosscorrelation of Ch" + otherChannelsKeys[0] + " and Ch" + otherChannelsKeys[1]);
			tempResultsTable = moduleService.run(ccc, false,
		        "dataset1", trimToRegion(otherChannels.get(otherChannelsKeys[0]), thisRegion),
		        "dataset2", trimToRegion(otherChannels.get(otherChannelsKeys[1]), thisRegion),
		        "maskAbsent", false,
		        "maskDataset", RAItoDataset(thisRegion, otherChannels.get(otherChannelsKeys[0]).getImgPlus()),
		        "significantDigits", 4,
		        "generateContributionImages",true,
		        "showIntermediates",false ,
		        "saveFolder", new File(rootSave + "GFPpos" + File.separator + (thisRegionLabel+1) + File.separator + "Ch" + otherChannelsKeys[0] + "xCh" + otherChannelsKeys[1] + File.separator)
		    	).get().getOutput("resultsTable");
		    	
		    correlationNameList.add("GFPPos cell " + (thisRegionLabel+1) + " - Ch" + otherChannelsKeys[0] + "xCh" + otherChannelsKeys[1] );
	    	addResultsToRunning(runningTableOfCorrelations, tempResultsTable); 
		}
    });
	
	//region GFP Negative 
	//have to trim original image when running CCC, to size of LabelRegion
	LabelRegion<BoolType> gfpNegativeRegion = gfpStatus.getLabelRegion("gfpNegative");
	
//	HashMap<int, Table> gfpNegACResults = new HashMap<int, Table>();	
	otherChannels.forEach((chNum, chImage) ->
	{
		println("Analyzing GFP Negative autocorrelation of Ch" + chNum);
	    tempResultsTable = moduleService.run(ccc, false,
	        "dataset1", trimToRegion(chImage, gfpNegativeRegion),
	        "dataset2", trimToRegion(chImage, gfpNegativeRegion),
	        "maskAbsent", false,
	        "maskDataset", RAItoDataset(gfpNegativeRegion, chImage.getImgPlus()),
	        "significantDigits", 4,
	        "generateContributionImages",true,
	        "showIntermediates",false ,
	        "saveFolder", new File(rootSave + "GFPneg" + File.separator  + "Ch" + chNum + File.separator)
	    	).get().getOutput("resultsTable");
	    
	    correlationNameList.add("GFPneg - Ch" + chNum + "xCh" + chNum );
	    addResultsToRunning(runningTableOfCorrelations, tempResultsTable); 
	    
	});
	
	if(otherChannelsKeys.length == 2){
		println("Analyzing GFP Negative crosscorrelation of Ch" + otherChannelsKeys[0] + " and Ch" + otherChannelsKeys[1]);
		tempResultsTable = moduleService.run(ccc, false,
	        "dataset1", trimToRegion(otherChannels.get(otherChannelsKeys[0]), gfpNegativeRegion),
	        "dataset2", trimToRegion(otherChannels.get(otherChannelsKeys[1]), gfpNegativeRegion),
	        "maskAbsent", false,
	        "maskDataset", RAItoDataset(gfpNegativeRegion, otherChannels.get(otherChannelsKeys[0]).getImgPlus()),
	        "significantDigits", 4,
	        "generateContributionImages",true,
	        "showIntermediates",false ,
	        "saveFolder", new File(rootSave + "GFPneg" + File.separator + "Ch"  + otherChannelsKeys[0] + "x" + otherChannelsKeys[1] + File.separator)
	    	).get().getOutput("resultsTable");
	   
	   	correlationNameList.add("GFPneg - Ch" + otherChannelsKeys[0] + "xCh" + otherChannelsKeys[1]);
	    addResultsToRunning(runningTableOfCorrelations, tempResultsTable); 
	}
	
	//endregion
	

	println(thisDataset.getName() + " analyzed successfully, moving to next image.");
	Table concatenatedTable = Tables.wrap(runningTableOfCorrelations, correlationNameList);
	
	ioService.save(concatenatedTable, rootSave + thisDataset.getName() + "-concatenatedTable.csv");
	//uiService.show(concatenatedTable);
}

println("Script finished successfully!");
