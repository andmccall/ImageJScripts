/**
 * This macro script analyzes two-channel images of organoids, with the first slice as fluorescence (GFP),
 * and the second as brightfield. The script uses the BF channel of each image to find the slice with the highest contrast,
 * taken as the slice with the highest mean value after a variance filter, and measures the mean GFP of the organoid at that slice.
 * Multiple organoids with the same image will each be given their own row in the results table.
 * 
 * Requires Fiji, but no additional plugins. 
 * 
 * @param inputFiles Image files to be analyzed. Expected to be Imaris image files (.ims)
 * @param outputDir Directory where the .csv table of results will be saved. 
 * 
 * @author Andrew McCall (University at Buffalo)
 */
 
cameraOffset = 113; //This value is subtracted from the GFP image prior to measurement. 
minOrganoidSize = 1000.0; //size is area in square microns 
 

#@ String (visibility = MESSAGE, value="Drag and drop a save folder to the field below.", required=false) msg
#@ File[] (label="Select images", style="files") inputFiles
#@ File (label="Table output directory", style="directory", persist=true) outputDir

setBatchMode("hide");

Table.create("Organoid results");
tableIndex = 0;

for (i = 0; i < inputFiles.length; i++) {
	run("Bio-Formats Importer", "open=["+inputFiles[i]+"] autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT series_1");
	title = getTitle();
	
	run("Split Channels");
	
	selectImage("C1-"+title);
	run("Subtract...", "value="+cameraOffset+" stack");
	setOption("Changes", false);
	
	selectImage("C2-"+title);
	run("32-bit");
	run("Variance...", "radius=1 stack");
	
	maxMean = 0.0;
	maxSlice = -1;
	
	for (j = 1; j <= nSlices; j++) {
    	setSlice(j);
    	getRawStatistics(nPixels, mean, min, max, std, histogram);
    	if(mean > maxMean){
    		maxMean = mean;
    		maxSlice = j;
    	}
	}
	setSlice(maxSlice);
	setOption("BlackBackground", true);
	setAutoThreshold("Triangle dark");
	run("Convert to Mask", "method=Triangle background=Dark black");
	
	run("Close-", "slice");
	run("Fill Holes", "slice");
	run("Set Measurements...", "area mean redirect=[C1-"+title+"] decimal=3");
	run("Analyze Particles...", "size="+minOrganoidSize+"-Infinity show=[Overlay Masks] display clear slice");
	
	setOption("Changes", false);
	close("C1-"+title);
	close("C2-"+title);
	for (j = 0; j < nResults(); j++) {
		area = getResult('Area', j);
		mean = getResult('Mean', j);
		selectWindow("Organoid results");
		Table.set("Image - number", tableIndex, title + " - " + j);
		Table.set("Area", tableIndex, area);
		Table.set("Mean GFP", tableIndex, mean);
	   	tableIndex++;
	}
	Table.update("Organoid results");
	Table.save(outputDir + File.separator + "OrganoidResults.csv");
}

setBatchMode("exit and display");

print("Finished");

