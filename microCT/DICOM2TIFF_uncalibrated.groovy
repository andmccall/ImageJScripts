/**
 * Script that converts DICOM image sequence stacks and saves them as TIFF stacks
 * Uses File > Import > Image Sequence to function to open DICOMs
 * 
 * @input A directory containing directories of DICOM sequences stacks
 * 
 * @output Automatically saved TIFF files to provided output folder
 * 
 * @author Andrew McCall
 */

import ij.plugin.FolderOpener;

import net.imagej.Dataset;
import io.scif.config.SCIFIOConfig;

#@ String (visibility = MESSAGE, value="Set directory in field below.", required=false) msg
#@ File (label="Select directory of DICOM directories", style = "directory") dicomDirectory
#@ File (label="Select save location", style="directory") outputDir
#@ CommandService commandService
#@ ConvertService converter
#@ DatasetIOService dios

File[] folderList = dicomDirectory.listFiles();

config = new SCIFIOConfig();
config.writerSetFailIfOverwriting(false);

for (int i = 0; i < folderList.length; ++i) {
	if(!folderList[i].isDirectory()){
		continue;
	}
	println("Opening DICOM folder: " + folderList[i].getPath());
	Dataset currentDS = converter.convert(FolderOpener.open(folderList[i].getPath(), ""), net.imagej.Dataset.class);
	
	dios.save(currentDS, outputDir.getPath() + File.separator + currentDS.getName() + ".tif", config);
}
println("All Finished!");
