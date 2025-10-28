// Currently a WIP

import ij.IJ;
import ij.WindowManager;

#@ File[] (label="Select images", style="files") inputFiles
#@ File (label="Select save location", style="directory") outputDir

#@ OpService ops
#@ ConvertService converter
#@ DatasetIOService datasetIOService



for(File file:inputFiles){
	
//	IJ.run("Bio-Formats Importer", "open=["+file.getPath()+"] autoscale color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
//	def image = converter.convert(WindowManager.getCurrentImage(), net.imagej.Dataset.class);
	
	def image = datasetIOService.open(file.getPath());
	
		
	IJ.run("HDF5/N5/Zarr/OME-NGFF ...", "containerroot=["+outputDir.getPath() +File.separator + image.getName() + ".zarr]" + "dataset=[" + image.getName() + "] storageformat=Zarr chunksizearg=128 createpyramidifpossible=true downsamplemethod=Sample compressionarg=gzip metadatastyle=OME-NGFF nthreads=40 overwrite=true");
	
	image.close();
	
}
