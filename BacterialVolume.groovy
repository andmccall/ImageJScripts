/*
 * Script that opens a two channel 3D image (must be two channel) of bacteria, 
 * and calculates the volume of each species into a table.
 */

import ij.IJ;
import net.imagej.axis.Axes;
import net.imagej.Dataset;
import net.imglib2.view.Views
import net.imglib2.histogram.Histogram1d;
import net.imglib2.histogram.Real1dBinMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.scijava.module.ModuleInfo;
import org.scijava.table.Table;
import org.scijava.table.Tables;

#@ File (label="Select image directory", style = "directory") folder
#@ File (label="Select classifier", style = "file") classifier
#@ UIService uiService
#@ ModuleService moduleService
#@ OpService ops
#@ DatasetIOService datasetioService
#@ IOService ioService

Table concatenatedTable;

ArrayList<HashMap<String, Long>> runningTable = new ArrayList<>();
ArrayList<String> imageNames = new ArrayList<>();

ModuleInfo labkit = moduleService.getModuleById("command:sc.fiji.labkit.ui.plugin.SegmentImageWithLabkitPlugin");

File[] fileList = folder.listFiles();


for (int i = 0; i < fileList.length; ++i) {
	if(datasetioService.canOpen(fileList[i].getPath())){

		image = datasetioService.open(fileList[i].getPath());
		
		Dataset labeledImage = moduleService.run(labkit, false,
			"input", image,
			"segmenter_file", classifier
		).get().getOutput("output");
		
		Histogram1d hist = new Histogram1d(new Real1dBinMapper(0, 2, 3, false));
		
		double volume = image.axis(Axes.X).get().calibratedValue(1) * 
			image.axis(Axes.Y).get().calibratedValue(1) * 
			image.axis(Axes.Z).get().calibratedValue(1);
		
		hist.countData(labeledImage);
		
		imageNames.add(image.getName());
		runningTable.add(
			Maps.newHashMap(
				ImmutableMap.of(
					"Green", hist.frequency(1)*volume, 
					"Red", hist.frequency(2)*volume
				)
			)
		);
	}
}
concatenatedTable = Tables.wrap(runningTable, imageNames);

uiService.show(concatenatedTable);
ioService.save(concatenatedTable, folder.getPath() + File.separator + "concatenatedTable.csv");
