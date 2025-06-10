/**
 * Simple script that measures the mean and stDev of two channels (Channels 2 and 3) across an entire image.
 * Automatically creates publication images and saves them as .PNG with 100 µm scalebar.
 * Automatically saves output table, concatendatedTable.csv, to provided output folder. 
 *  
 * 
 * @param fileList List of image files to run
 * @param Output folder location for automatic table saving
 * 
 * @author Andrew McCall
 **/

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.ZProjector;
import ij.plugin.Duplicator;

import net.imglib2.img.Img;
import net.imagej.axis.Axes;
import net.imagej.Dataset;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.scijava.module.ModuleInfo;
import org.scijava.table.Table;
import org.scijava.table.Tables;

#@ File[] (label="Select images", style = "files") fileList
#@ File (label="Table output directory", style="directory", persist=true) outputDir
#@ UIService uiService
#@ OpService ops
#@ ConvertService convertService
#@ DatasetIOService datasetioService
#@ IOService ioService

Table concatenatedTable;

ArrayList<HashMap<String, Long>> runningTable = new ArrayList<>();
ArrayList<String> imageNames = new ArrayList<>();

for (int i = 0; i < fileList.length; ++i) {
	if(!datasetioService.canOpen(fileList[i].getPath())){
		println("Skipping non-image file: " + fileList[i].getPath());
		continue;
	}

	println("Opening image " + fileList[i].getPath());
	image = datasetioService.open(fileList[i].getPath());

//output Image
	println("Creating publication image");
	imp = convertService.convert(image, ij.ImagePlus.class);
	imp = ZProjector.run(imp,"max");
	
	//Adjust Brightness and Contrast here
	imp.setC(1);
	IJ.run(imp, "Blue", "");
	imp.setDisplayRange(135, 280);
	imp.setC(2);
	IJ.run(imp, "Green", "");
	imp.setDisplayRange(300, 5000);
	imp.setC(3);
	IJ.run(imp, "Red", "");
	imp.setDisplayRange(100, 180);
	
	IJ.run(imp, "Scale Bar...", "width=100 height=50 thickness=50 bold hide overlay");	
	imp = new Duplicator().run(imp, 1, 3, 1, 1, 1, 1);	
	new File(fileList[i].getParent() + File.separator + "PNGs" + File.separator).mkdir();	
	IJ.saveAs(imp, "png", fileList[i].getParent() + File.separator + "PNGs" + File.separator + image.getName() + ".png");
		
	println("Analyzing Image");
  imageNames.add(image.getName());
	runningTable.add(
		ImmutableMap.of(
			"Mean intensity (AQP5)", ops.stats().mean(ops.transform().hyperSliceView(image, image.dimensionIndex(Axes.CHANNEL), 1)),
			"St Dev (AQP5)", ops.stats().stdDev(ops.transform().hyperSliceView(image, image.dimensionIndex(Axes.CHANNEL), 1)),
			"Mean intensity (Ck7)", ops.stats().mean(ops.transform().hyperSliceView(image, image.dimensionIndex(Axes.CHANNEL), 2)),
			"St Dev (Ck7)", ops.stats().stdDev(ops.transform().hyperSliceView(image, image.dimensionIndex(Axes.CHANNEL), 2)),
		)
	)

}
concatenatedTable = Tables.wrap(runningTable, imageNames);

uiService.show(concatenatedTable);
ioService.save(concatenatedTable, outputDir.getPath() + File.separator + "ConcatenatedTable.csv");

println("All done!");
