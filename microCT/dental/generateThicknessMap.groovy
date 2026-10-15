/* This script saves the local thickness map of the cement between inserts and crowns in microCT images.
 *  
 *  Also requires a LabKit classifier that separates background from the implant/tooth and crown 
 *  (one object), and the cement.
 * 
 * @author Andrew McCall
 */

import org.scijava.command.CommandModule;

import net.imagej.axis.Axes;
import net.imagej.Dataset;
import net.imagej.ImgPlus;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;


import io.scif.config.SCIFIOConfig;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.roi.labeling.ImgLabeling;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;


#@ File[] (label="Select registered microCT images", style = "files") fileList
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

config = new SCIFIOConfig();
config.writerSetFailIfOverwriting(false);
config.put("WRITE_BIG_TIFF", true);

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
	
		
	println("Creating mask of cement");
	Img<BitType> cement = ops.create().img(image, new BitType());	
	copyLabelRegion2Img(cement, materialSelector.getLabelRegion("cement"));
//	uiService.show(cement);
    
	
	println("Generating thickness map");
	ImagePlus cementImp = converter.convert(ImgtoDataset(cement, image.getImgPlus()), ij.ImagePlus.class);
	IJ.run((ImagePlus)cementImp, "Local Thickness (complete process)", "threshold=128");
	Img thicknessImage = converter.convert(WindowManager.getCurrentImage(), net.imglib2.img.Img.class);
	
	thicknessImage = ops.math().multiply(thicknessImage.getImgPlus().getImg(), scale);
	
	datasetioService.save(ImgtoDataset(thicknessImage, image.getImgPlus()), outputDir.getPath() + File.separator + image.getName() + "-thicknessMap.tif", config);
	
	for(String openImageName:WindowManager.getImageTitles()){
		WindowManager.getImage(openImageName).changes = false;
		WindowManager.getImage(openImageName).close();
	}

}

uiService.show("All done!");
