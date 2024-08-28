/**
 * A relatively simple script that segments each open image via a smoothed DAPI 
 * channel, and outputs the mean intensity with std dev of that region for all 
 * other channels to a table. 
 * 
 * @author Andrew McCall
*/

double gaussDiam = 10.0;

import ij.IJ;
import ij.WindowManager;
import net.imagej.axis.Axes;
import net.imagej.Dataset;
import net.imagej.ImgPlus;

import net.imglib2.img.Img;
import net.imglib2.IterableInterval;
import net.imglib2.FinalInterval;
import net.imglib2.roi.Regions;
import net.imglib2.roi.util.IterableRegionOnBooleanRAI;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.scijava.module.ModuleInfo;
import org.scijava.table.Table;
import org.scijava.table.Tables;

#@ Integer (label="DAPI Channel", value=1, persist=true) dapi
#@ ModuleService moduleService
#@ ConvertService convertService
#@ UIService uiService
#@ OpService ops

--dapi;

println("Found " + WindowManager.getIDList().length + " open images.");
List<Dataset> imageList = new ArrayList();
for (id in WindowManager.getIDList()) {
	imageList.add(convertService.convert(WindowManager.getImage(id),  net.imagej.Dataset.class));
}

ArrayList<HashMap<String, Long>> runningTableOfValues = new ArrayList<>();
ArrayList<String> channelNameList = new ArrayList<>();

for(Dataset thisDataset: imageList){
	println("Analyzing " + thisDataset.getName());
	
	int channelAxis = thisDataset.dimensionIndex(Axes.CHANNEL);
	
	long[] min = new long[thisDataset.numDimensions()];
    long[] max = new long[thisDataset.numDimensions()];
    for(int d = 0; d < min.length; ++d){
        min[d] = thisDataset.min(d);
        max[d] = thisDataset.max(d);
    }
    min[channelAxis] = dapi;
    max[channelAxis] = dapi;    
    dapiCh = ops.transform().crop(thisDataset, new FinalInterval(min, max), true);
	
	println("Gaussian smoothing");
	smoothed = ops.filter().gauss(dapiCh, new double[] {gaussDiam/thisDataset.axis(Axes.X).get().calibratedValue(1), gaussDiam/thisDataset.axis(Axes.Y).get().calibratedValue(1), gaussDiam/thisDataset.axis(Axes.Z).get().calibratedValue(1)});
	//uiService.show(smoothed);
	
	println("Segmenting");
	//Can change "otsu" to "huang", "rosin", "li", or "yen"; case-sensitive
	Img segmented = ops.threshold().otsu(smoothed);
	//uiService.show(segmented);

	IterableRegionOnBooleanRAI dapiRegion = new  IterableRegionOnBooleanRAI(segmented);
	
	println("Measuring channels");
	for (int currentCh = 0; currentCh < thisDataset.dimension(Axes.CHANNEL); currentCh++) {
		if(currentCh == dapi){
			continue;
		}
		
		min[channelAxis] = currentCh;
    	max[channelAxis] = currentCh;
    	Img currentImg = ops.transform().crop(thisDataset, new FinalInterval(min,max), true);
		
		IterableInterval sampledCh = Regions.sample(dapiRegion, currentImg);
		
		channelNameList.add(thisDataset.getName() + "-Ch" + (currentCh+1));
		runningTableOfValues.add(
            Maps.newHashMap(
                ImmutableMap.of(
                	"Mean", ops.stats().mean(sampledCh),
                	"StDev", ops.stats().stdDev(sampledCh)
            	)
        	)
    	);
	}
}
Table concatenatedTable = Tables.wrap(runningTableOfValues, channelNameList);
uiService.show(concatenatedTable);
