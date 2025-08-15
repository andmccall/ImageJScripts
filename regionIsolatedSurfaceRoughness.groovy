/**
 * Script to analyze surface roughness of input z-stack surface profilometry images.
 * Also saves table to designated folder location.
 * 
 * @param z-stack surface profilometry images
 * @param Output directory location to save 'ConcatenatedTable.csv' in
 * 
 * @author Andrew McCall
 */

//Cutoffs for roughness in scaled units; used to seed Gaussian blur sigma value
float lowerCutoff = 0.5;
float upperCutoff = 250;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.ImageCalculator;
import ij.plugin.ZProjector;

import io.scif.config.SCIFIOConfig;

import com.google.common.collect.ImmutableMap;

import net.imagej.Dataset;

import net.imglib2.loops.LoopBuilder;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.roi.Regions;
import net.imglib2.roi.mask.integer.RandomAccessibleIntervalAsMaskInterval;

import org.scijava.table.Table;
import org.scijava.table.Tables;


#@ String (visibility = MESSAGE, value="Drag and drop image files to field below.", required=false) msg
#@ File[] (label="Select images", style="files") inputFiles
#@ File (label="Select save location", style="directory") outputDir
#@ UIService uiService
#@ OpService ops
#@ IOService ioService
#@ ConvertService convert
#@ DatasetService datasetService
#@ DatasetIOService datasetIOService



config = new SCIFIOConfig();
config.writerSetFailIfOverwriting(false);

Table concatenatedTable;

RectangleShape shape = new RectangleShape(3, true);

ArrayList<LinkedHashMap<String, Float>> runningTable = new ArrayList<>();
ArrayList<String> imageNames = new ArrayList<>();

def closeImage(ImagePlus toClose){
	toClose.changes = false;
	toClose.close();
}

for(File file:inputFiles){
	if(!datasetIOService.canOpen(file.getPath())){
		println(file.getPath() + " - cannot be opened as an image by Fiji, skipping file.");
		continue;
	}
	
	//Using datasetIOService to open IMS files
	println("Opening: " + file.getPath());
	imp = convert.convert(datasetIOService.open(file.getPath()), ij.ImagePlus.class);
	saveFolder = outputDir.getPath() + File.separator + imp.getShortTitle() + File.separator;
	new File(saveFolder).mkdirs();
	//imp =  IJ.openImage(file.getPath());
	imageNames.add(imp.getShortTitle());
	zProj = ZProjector.run(imp,"max");
	IJ.saveAs(zProj, "Tiff", saveFolder + "z-Projection.tif");
	
	analysisMask = 
	ops.morphology().erode(
		ops.morphology().erode(
			ops.morphology().fillHoles(
				ops.morphology().dilate(
					ops.threshold().huang(convert.convert(zProj,net.imagej.Dataset.class))
				, shape)
			)
		,shape)
	,shape);
	//uiService.show(analysisMask);
	datasetIOService.save(convert.convert(ops.convert().int8(analysisMask), net.imagej.Dataset.class), saveFolder + "analyzedRegion.tif", config);
	
	closeImage(zProj);
	println("Computing topography");
	IJ.run(imp, "Compute Topography", "height threshold=0 quadratic_0=20");
	topoImage = WindowManager.getCurrentImage();
	IJ.run(topoImage, "Remove Slope", "");
	
	IJ.run(topoImage, "Duplicate...", "title=roughness");
	roughness = WindowManager.getCurrentImage();
	IJ.run(topoImage, "Duplicate...", "title=waviness");
	waviness = WindowManager.getCurrentImage();
	closeImage(topoImage);
			
	println("Subtracting waviness");
	IJ.run(roughness, "Gaussian Blur...", "sigma="+lowerCutoff+" scaled");
	IJ.run(waviness, "Gaussian Blur...", "sigma="+upperCutoff+" scaled");
	
	//Doesn't work without the create, not sure why
	resultOfRoughness = ImageCalculator.run(roughness, waviness, "Subtract create 32-bit");
	closeImage(roughness);
	closeImage(waviness);
	
	roughnessDS = convert.convert(resultOfRoughness, net.imagej.Dataset.class);
	closeImage(resultOfRoughness);
	
	datasetIOService.save(roughnessDS, saveFolder + "roughness.tif", config);
	
	println("Calculating absolute value image");
	absRoughness = roughnessDS.copy();

	absRoughness.cursor().forEachRemaining((pixel) -> {
		if(pixel.getRealFloat()<0)
				pixel.setReal(-1*(pixel.getRealFloat()));
		}
	);
	
	roughnessII = Regions.sampleWithMask(new RandomAccessibleIntervalAsMaskInterval(analysisMask), roughnessDS);
	absRoughnessII = Regions.sampleWithMask(new RandomAccessibleIntervalAsMaskInterval(analysisMask), absRoughness);
	
	println("Concatenating surface metrics");
	runningTable.add(
		ImmutableMap.of(
			"Mean (Sa)", ops.stats().mean(absRoughnessII), 
			"RMS (Sq)", ops.stats().stdDev(absRoughnessII),
			"Skew (Ssk)", ops.stats().skewness(roughnessII),
			"Kurtosis (Sku)", ops.stats().kurtosis(roughnessII),
			"Min (Sv)", ops.stats().min(roughnessII),
			"Max (Sz)", ops.stats().max(roughnessII)
		)
	);
	concatenatedTable = Tables.wrap(runningTable, imageNames);
	ioService.save(concatenatedTable, outputDir.getPath() + File.separator + "ConcatenatedTable.csv");
}

uiService.show(concatenatedTable);
