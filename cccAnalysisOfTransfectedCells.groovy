/**
 * The purpose of this script is to concatenate CCC, Pearsons, and M1 and M2 results from multiple images into one file.
 * Each transfected GFP+ cell in each image is processed separately.
 * The script is setup to perform some of the pre-processing steps for CCC (except decon). 
 * The script also assumes multi-channel files with Channel 3 and 4 as the channels to evaluate
 * for spatial cross-correlation.
 * Requries a labkit classifier trained on the original image data.
 * 
 * Requires Colocalization by cross-correlation, and Labkit (CLIJ and CLIJ2 optional for GPU acceleration)
 * 
 * @author Andrew McCall
 */

import net.imagej.axis.Axes;
import net.imagej.Dataset;
import net.imagej.ImgPlus;

import org.scijava.table.Table;
import org.scijava.table.Tables;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.view.Views;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.roi.labeling.*;
import net.imglib2.roi.Masks;
import net.imglib2.roi.Regions;
import net.imglib2.roi.util.IterableRegionOnBooleanRAI;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

#@ File[] (label="Select images to process", style="files") fileList
#@ File (label="Classifier file from Labkit", style = "file") classifier
#@ File (label="Set output folder", style="directory") outputDir
#@ boolean (label="NVIDIA card and CLIJ2 installed?") useGPU
#@ UIService uiService
#@ OpService ops
#@ ModuleService moduleService
#@ IOService ioService
#@ DatasetIOService dsioService
#@ DatasetService datasetService

int bckgSubtract = 103;
int ch1index=2, ch2index=3;

float minRegionSize = 150.0; //Min cell size in cubic microns

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

def setName(Dataset toSet, String name){
	toSet.setName(name);
	return toSet;
}

Table concatenatedTable;

ArrayList<HashMap<String, Object>> runningTable = new ArrayList<>();
ArrayList<String> imageNames = new ArrayList<>();

ccc = moduleService.getModuleById("command:CCC.Colocalization_by_Cross_Correlation");
labkit = moduleService.getModuleById("command:sc.fiji.labkit.ui.plugin.SegmentImageWithLabkitPlugin");

for (int i = 0; i < fileList.length; ++i) {
	println(fileList[i].getPath());
	if(!dsioService.canOpen(fileList[i].getPath())){
		println("Skipping non-image file: " + fileList[i].getPath());
		continue;
	}
	println("Opening image: " + fileList[i].getPath());
    Dataset originalImage = dsioService.open(fileList[i].getPath());
    println("Converting to 32-bit");
    Dataset image = datasetService.create(ops.create().imgPlus(ops.convert().float32((Img) originalImage.getImgPlus()), originalImage));
    
    channelAxis = image.dimensionIndex(Axes.CHANNEL);
    float pixelVolume = image.axis(Axes.X).get().calibratedValue(1) * image.axis(Axes.Y).get().calibratedValue(1) * image.axis(Axes.Z).get().calibratedValue(1);
    
    println("Splitting channels");
    long[] min = new long[image.numDimensions()];
    long[] max = new long[image.numDimensions()];
    for(int d = 0; d < min.length; ++d){
    	min[d] = image.min(d);
    	max[d] = image.max(d);
    } 
    min[channelAxis] = ch1index;
    max[channelAxis] = ch1index;
    
    FinalInterval ch1Int = new FinalInterval(min, max);
    ch1 = ops.transform().crop(image, ch1Int , true);
    
    min[channelAxis] = ch2index;
    max[channelAxis] = ch2index;
    FinalInterval ch2Int = new FinalInterval(min, max);
    ch2 = ops.transform().crop(image, ch2Int , true);
   
	println("Segmenting image");
	
	Dataset mask = moduleService.run(labkit, false,
		"input", originalImage,
		"segmenter_file", classifier,
		"use_gpu", useGPU
	).get().getOutput("output");

//	uiService.show(mask);    
        
    RealType x = image.getType();
    x.setReal(bckgSubtract);
    ch1 = datasetService.create(ops.create().imgPlus(ops.math().subtract(ch1, x), ch1));
    ch2 = datasetService.create(ops.create().imgPlus(ops.math().subtract(ch2, x), ch2));
    
    ImgLabeling<Integer, BoolType> labels = ops.labeling().cca(mask, StructuringElement.EIGHT_CONNECTED);
    
//    uiService.show(labels.getIndexImg());

    LabelRegions<BoolType> regions = new LabelRegions(labels);
    
    println("Starting CCC on each cell");

    regions.getExistingLabels().forEach(thisRegionLabel -> {
        LabelRegion<BoolType> thisRegion = regions.getLabelRegion(thisRegionLabel);
        float calibratedRegionSize = thisRegion.size()*pixelVolume;
        if(calibratedRegionSize <= minRegionSize) {
        	//println("Region size not within range: " + calibratedRegionSize);
        	return;
        }
        
        trimCh1 = setName(trimToRegion(ch1, thisRegion), image.getName() + "-ch" + ch1index);
        trimCh2 = setName(trimToRegion(ch2, thisRegion), image.getName() + "-ch" + ch2index);
        
        uiService.show(trimCh1);
        uiService.show(trimCh2);
        
        cellMask = setName(RAItoDataset(thisRegion, ch1.getImgPlus()),"CellMask-"+(thisRegionLabel+1));
        
		println("Starting CCC on region " + (thisRegionLabel+1));
		
	    Table tableOut = moduleService.run(ccc, false, 
	    	"dataset1", trimCh1, 
	    	"dataset2",trimCh2, 
	    	"maskAbsent", false, 
	    	"maskDataset",cellMask, 
	    	"significantDigits", 4, 
	    	"generateContributionImages",true, 
	    	"showIntermediates",false ,
	    	"saveFolder", outputDir.getPath() + File.separator + "CellResults" + File.separator + image.getName() + File.separator + (thisRegionLabel+1)
    	).get().getOutput("resultsTable");
	
	    imageNames.add(image.getName()+"-"+(thisRegionLabel+1));
	    
	    println("Calculating Mander's coefficients");
	    //Moments seems to give most reasonable cutoff
	    
	    ch1Bit = ops.threshold().moments(trimCh1);
	    ch2Bit = ops.threshold().moments(trimCh2);
	    
	    uiService.show(ch1Bit);
        uiService.show(ch2Bit);
	    
	    chOverlap = Masks.toIterableRegion(Masks.toMaskInterval(ch1Bit).and(Masks.toMaskInterval(ch2Bit)));
	    
	    //println((double)ops.stats().sum(Regions.sample(chOverlap, trimCh1))/(double)ops.stats().sum(Regions.sample(new IterableRegionOnBooleanRAI(ch1Bit), trimCh1)));

	    runningTable.add(
    		ImmutableMap.builder()
    			.put("CCC-Mean", tableOut.get(0,0)) 
    			.put("CCC-StDev", tableOut.get(0,1)) 
    			.put("CCC-Confidence", tableOut.get(0,2))
    			.put("CCC-R-Squared", tableOut.get(0,3))
    			.put("CCC-Gaussian Height", tableOut.get(0,4))
    			.put("Pearsons", ops.coloc().pearsons(Regions.sample(thisRegion, ch1), Regions.sample(thisRegion, ch2)))
    			.put("M1", (ops.stats().sum(Regions.sample(chOverlap, trimCh1)).get()/ops.stats().sum(Regions.sample(new IterableRegionOnBooleanRAI(ch1Bit), trimCh1)).get()))
    			.put("M2", (ops.stats().sum(Regions.sample(chOverlap, trimCh2)).get()/ops.stats().sum(Regions.sample(new IterableRegionOnBooleanRAI(ch2Bit), trimCh2)).get()))
    			.build()	
	    );
    });
    
    if(runningTable.isEmpty())
    	continue;
	concatenatedTable = Tables.wrap(runningTable, imageNames);
	ioService.save(concatenatedTable, outputDir.getPath() + File.separator + "concatenatedTable.csv");
}

uiService.show(concatenatedTable);

println("Finished");
