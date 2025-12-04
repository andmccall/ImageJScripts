/** This script is for the automated analysis of microglia. It requires Labkit and SNT to be installed.
 *  It also requires a LabKit classifier that segments the microglia from the background, though the script
 *  will automatically process out objects either too small or too large (see parameters below).
 *  
 *  The list of output table columns can be modified. The instructions for this are near the bottom of the script, 
 *  above where the parameters will be modified.
 *  
 *  @author Andrew McCall 
 */
 

//parameters:
//Soma Eucledian Distance Transform parameters:
//Minimum cell body radius in microns
float minEDTvalue = 1.0;
//Minimum distance center of cell body must be from image border in microns
float inclusiveEDTborder = 2;


//RegionSizeLimit (size limit of the full cell in cubic microns):
float minRegionSize = 570;
float maxRegionSize = 2700;

//SNT:
int somaSearchDiameter = 4;
boolean pruneByLength = true;
double pruneLengthThreshold = 1.0;
boolean connectComponents = true;
double connectComponentsDistance = 2.0;
double spineInclMaxLength = 4.0;
//Sholl radius step size in microns
double shollStepSize = 1.0;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;

import io.scif.config.SCIFIOConfig;

import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.imagej.axis.Axes;
import net.imagej.Dataset;
import net.imagej.ImgPlus;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealLocalizable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.histogram.Integer1dBinMapper;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.roi.mask.integer.RandomAccessibleIntervalAsMaskInterval;
import net.imglib2.roi.labeling.*;
import net.imglib2.roi.Regions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.img.ImgView;
import net.imglib2.view.Views;
import net.imglib2.loops.IntervalChunks;
import net.imglib2.parallel.Parallelization;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.algorithm.morphology.distance.DistanceTransform;
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.scijava.module.Module;
import org.scijava.module.ModuleInfo;
import org.scijava.table.Table;
import org.scijava.table.Tables;

import sc.fiji.snt.*
import sc.fiji.snt.analysis.*
import sc.fiji.snt.analysis.graph.*
import sc.fiji.snt.analysis.sholl.*
import sc.fiji.snt.analysis.sholl.math.*
import sc.fiji.snt.analysis.sholl.parsers.*
import sc.fiji.snt.annotation.*
import sc.fiji.snt.io.*
import sc.fiji.snt.plugin.*
import sc.fiji.snt.util.*
import sc.fiji.snt.viewer.*;

#@ File[] (label="Select image[s]", style = "files") fileList
#@ File (label="Full cell classifier", style = "file") fullCellClassifier
//#@ File (label="Soma classifier", style = "file") somaClassifier
#@ int (label="Microglia channel:") microgliaChannel
#@ UIService uiService
#@ Context context
#@ ModuleService moduleService
#@ ConvertService converter
#@ OpService ops
#@ DatasetService dsService
#@ DatasetIOService datasetioService
#@ IOService ioService
#@ SNTService sntService

ModuleInfo labkit = moduleService.getModuleById("command:sc.fiji.labkit.ui.plugin.SegmentImageWithLabkitPlugin");

--microgliaChannel;

def RAItoImagePlus(RandomAccessibleInterval input, ImgPlus Metadata){
    return converter.convert(
            dsService.create(
                    ops.create().imgPlus(
                            ImgView.wrap(input),
                            Metadata
                    )
            ),
            ij.ImagePlus.class
    )
}

def RAItoDataset(RandomAccessibleInterval input, ImgPlus Metadata){
    return dsService.create(
                ops.create().imgPlus(
                        ImgView.wrap(input),
                        Metadata
                )
	       )	
}

def listToMap(List<Double> keys, List<Double> values){
	HashMap<String, Double> outMap = new HashMap();
	Iterator<Double> keyIter = keys.iterator();
    Iterator<Double> valIter = values.iterator();
    while(keyIter.hasNext() && valIter.hasNext()){
    	outMap.put(keyIter.next().toString(), valIter.next());
    }
    return outMap;
}


config = new SCIFIOConfig();
config.writerSetFailIfOverwriting(false);

for (int i = 0; i < fileList.length; ++i) {
    if(!datasetioService.canOpen(fileList[i].getPath())){
    	continue;
    }
	ArrayList<HashMap<String, Double>> runningTable = new ArrayList<>();
	ArrayList<HashMap<String, Double>> runningShollTable = new ArrayList<>();
	ArrayList<String> treeNames = new ArrayList<>();
	
	
    Dataset image = datasetioService.open(fileList[i].getPath());
    
    new File(fileList[i].getPath() +"-out" + File.separator).mkdirs();

	float pixelVolume = image.axis(Axes.X).get().calibratedValue(1) * image.axis(Axes.Y).get().calibratedValue(1) * image.axis(Axes.Z).get().calibratedValue(1);
    ImgPlus microglia;

    int channelAxis = image.dimensionIndex(Axes.CHANNEL);
    if(channelAxis != -1){
        long[] min = new long[image.numDimensions()];
        long[] max = new long[image.numDimensions()];
        for(int d = 0; d < min.length; ++d){
            min[d] = image.min(d);
            max[d] = image.max(d);
        }
        min[channelAxis] = microgliaChannel;
        max[channelAxis] = microgliaChannel;

        FinalInterval ch1Int = new FinalInterval(min, max);
        microglia = ops.transform().crop(image, ch1Int , true);
    }
    else{
        microglia = image.getImgPlus().copy();
    }

    //uiService.show(microglia);

    Future<Module> fullCellMod = moduleService.run(labkit, false,
            "input", microglia,
            "segmenter_file", fullCellClassifier
    );

    fullCell = fullCellMod.get().getOutput("output");
    //fullCell.setName("fullCell");
    //uiService.show(fullCell);     

    ImgLabeling<Integer, BoolType> labels = ops.labeling().cca(fullCell, StructuringElement.EIGHT_CONNECTED);
    
    config.put("WRITE_BIG_TIFF", true);
    datasetioService.save(RAItoDataset(labels.getIndexImg(), microglia), fileList[i].getPath() +"-out" + File.separator + "labeledCells.tif", config);
	config.put("WRITE_BIG_TIFF", false);

    LabelRegions<BoolType> regions = new LabelRegions(labels);

    regions.getExistingLabels().forEach(thisRegionLabel -> {
        LabelRegion<BoolType> thisRegion = regions.getLabelRegion(thisRegionLabel);
        float calibratedRegionSize = thisRegion.size()*pixelVolume;
        if(calibratedRegionSize <= minRegionSize || calibratedRegionSize >= maxRegionSize) {
        	println("Region size not within range: " + calibratedRegionSize);
        	return;
        }
        
        filledRegion = ops.morphology().fillHoles(ops.run("convert.bit", Views.zeroMin(thisRegion)));        
        //uiService.show(filledRegion);
        
        RandomAccessibleInterval edtImage = ops.image().distancetransform(filledRegion, new float[] {image.axis(Axes.X).get().calibratedValue(1), image.axis(Axes.Y).get().calibratedValue(1), image.axis(Axes.Z).get().calibratedValue(1)});        
        //uiService.show(edtImage);

        RealType maxEuclidean = ops.stats().max(edtImage);
        
        if(maxEuclidean.getRealFloat() < minEDTvalue){
        	println("No soma found, max EDT value: " + maxEuclidean.getRealFloat());
        	return;
        }        
        //uiService.show(ops.threshold().apply(edtImage, new FloatType((float)(maxEuclidean.getRealFloat()-0.1))));
        
        Double[] centroid = ops.geom().centerOfGravity(ops.threshold().apply(edtImage, new FloatType((float)(maxEuclidean.getRealFloat()-0.1)))).positionAsDoubleArray();
        long[] edtDims = edtImage.dimensionsAsLongArray();
        double[] edtBorders = new double[edtDims.length];
        
        
        centroid[0] = centroid[0] * image.axis(Axes.X).get().calibratedValue(1);
        centroid[1] = centroid[1] * image.axis(Axes.Y).get().calibratedValue(1);
        centroid[2] = centroid[2] * image.axis(Axes.Z).get().calibratedValue(1);
        
        edtBorders[0] = edtDims[0] * image.axis(Axes.X).get().calibratedValue(1);
        edtBorders[1] = edtDims[1] * image.axis(Axes.Y).get().calibratedValue(1);
        edtBorders[2] = edtDims[2] * image.axis(Axes.Z).get().calibratedValue(1);
        
        for(int dim = 0; dim < centroid.size(); ++dim){        	
        	if(centroid[dim] <= inclusiveEDTborder || centroid[dim] >= (edtBorders[dim]-inclusiveEDTborder)){
        		return;
        	}
        }
    	
    	//String treeName = (-thisRegion.origin().getLongPosition(0)) + "," + (-thisRegion.origin().getLongPosition(1)) + "," +(-thisRegion.origin().getLongPosition(2)) + "-" + thisRegionLabel;
        String thisRegionOutputFolder = fileList[i].getPath()+"-out" + File.separator + (thisRegionLabel+1) + File.separator;
        new File(thisRegionOutputFolder).mkdirs();

        ImagePlus thisRegionMask = RAItoImagePlus(filledRegion, microglia);

        thisRegionMask.setTitle(thisRegionLabel + "-mask");

        //uiService.show(thisRegionMask);

        thisRegionMask.show();
        IJ.setRawThreshold(thisRegionMask, 1, 255);
        IJ.run(thisRegionMask, "Convert to Mask", "background=Dark black");

        thisSkeletonized = IJ.getImage();
        IJ.run(thisSkeletonized, "Skeletonize (2D/3D)", "");					
		
		Roi somaCenter = new Roi(Math.round(centroid[0])-(somaSearchDiameter/2), Math.round(centroid[1])-(somaSearchDiameter/2), somaSearchDiameter, somaSearchDiameter);
		
		thisSkeletonized.setSlice((int)Math.round(centroid[2]));
		thisSkeletonized.changes = false;
				
        thisRegionImg = RAItoDataset(Views.zeroMin(ops.transform().intervalView(microglia, thisRegion)), microglia);
        //thisRegionImg.setTitle(thisRegion.size() + "-data");
        //uiService.show(thisRegionImg);
        datasetioService.save(thisRegionImg, thisRegionOutputFolder + (thisRegionLabel+1) + "-data.tif", config);
        
        Img thisRegionIsolatedImg = microglia.factory().create(thisRegion);            
        LabelRegionMaskApply: {
        	imgCursor = thisRegionImg.localizingCursor();
			regionRA = Views.zeroMin(thisRegion).randomAccess();
			outRA = thisRegionIsolatedImg.randomAccess();
			
			while(imgCursor.hasNext()){
				imgCursor.next();
				
				outRA.setPosition(imgCursor);            		
				regionRA.setPosition(imgCursor);
				
				regionRA.get().get() ? 
				outRA.get().set(imgCursor.get()) : 
				outRA.get().setZero();
			}   	
		}
		
        isolatedImagePlus = converter.convert(RAItoDataset(thisRegionIsolatedImg, microglia), ij.ImagePlus.class);
		
        IJ.run(isolatedImagePlus, "Enhance Contrast...", "saturated=0.05 use");
        IJ.run(isolatedImagePlus, "3D Project...", "projection=[Brightest Point] axis=Y-Axis slice=1 initial=0 total=358 rotation=2 lower=1 upper=255 opacity=0 surface=100 interior=50 interpolate");
        rotating = IJ.getImage();
        IJ.run(rotating, "Canvas Size...", "width="+(rotating.getWidth() + rotating.getWidth()%2)+" height="+(rotating.getHeight() + rotating.getHeight()%2)+" position=Top-Left zero");	            
        IJ.run(rotating, "Movie...", "frame=15 container=.mp4 using=MPEG4 video=excellent save=[" + thisRegionOutputFolder + (thisRegionLabel+1) + "-isolated.mp4]");
        rotating.changes = false;
        
        //SNT thisSNT = sntService.initialize(thisRegionMask, false);
        skeletonConverter = new SkeletonConverter(thisSkeletonized);
        skeletonConverter.setPruneByLength(pruneByLength);
        skeletonConverter.setLengthThreshold(pruneLengthThreshold);
        skeletonConverter.setConnectComponents(connectComponents);
        skeletonConverter.setMaxConnectDist(connectComponentsDistance);
        skeletonConverter.setOrigIP(isolatedImagePlus);
        skeletonConverter.setPruneMode(SkeletonConverter.LOWEST_INTENSITY_BRANCH);
        
        
        //skeletonConverter.setRootRoi(somaCenter, 32); 

        List<Tree> trees = skeletonConverter.getTrees(somaCenter, false);
        
        //Start of view
//        	viewer = new Viewer3D(context);
//        	Tree.assignUniqueColors(trees);
//        	viewer.add(trees);
//        	viewer.show();
    	//end of view
    	
    	if(trees.size() > 1){System.out.println("Error: more than one tree produced for a single cell");}
        
        treeNames.add("" + (thisRegionLabel+1));
        for(Tree tree:trees){
        	tree.get(0).setSWCType(Path.SWC_SOMA);
        	
        	for(Path path:tree.list()){
        		if(path.getChildren().size() == 0 && path.getLength() <= spineInclMaxLength){
        			path.getStartJoins().setSpineOrVaricosityCount(
        				path.getStartJoins().getSpineOrVaricosityCount()+1
        			)
        		}
        	}
        	
        	tree.saveAsSWC(thisRegionOutputFolder + thisRegion.size() + "-tree.swc");
        	
        	/*
        	 * The list below can be modified to change the reported results. To remove results from an analysis, simply add a '//' before the line.
        	 * This action "commments" the line, meaning it will not be read by the script reader, allowing easy removal and replacement as needed.
        	 * To comment out entire sections, select all lines and hit 'ctrl' + '/', do this again to uncomment
        	 */
        	treeStats = new TreeStatistics(tree);
        	runningTable.add(
        		Maps.newHashMap(
        			ImmutableMap.builder()
        				//Branches, nodes and paths
						.put("Soma radius", maxEuclidean.getRealFloat())
        				.put("Number of branches", treeStats.getSummaryStats(TreeStatistics.N_BRANCHES).getMean())            				
        				.put("Number of branch nodes",  treeStats.getSummaryStats(TreeStatistics.N_BRANCH_NODES).getMean())
        				.put("Number of total nodes",  treeStats.getSummaryStats(TreeStatistics.N_NODES).getMean())
        				.put("Number of branch points",  treeStats.getSummaryStats(TreeStatistics.N_BRANCH_POINTS).getMean())
        				.put("Number of paths",  treeStats.getSummaryStats(TreeStatistics.N_PATHS).getMean())
        				.put("Path length",  treeStats.getSummaryStats(TreeStatistics.PATH_LENGTH).getMean())
        				.put("Branch length",  treeStats.getSummaryStats(TreeStatistics.BRANCH_LENGTH).getMean())
        				.put("Branch Contraction",  treeStats.getSummaryStats(TreeStatistics.BRANCH_CONTRACTION).getMean())
        				.put("Branch fractal dimension",  treeStats.getSummaryStats(TreeStatistics.BRANCH_FRACTAL_DIMENSION).getMean())
        				//.put("Branch volume",  treeStats.getSummaryStats(TreeStatistics.BRANCH_VOLUME).getMean())
        				.put("Internode distance",  treeStats.getSummaryStats(TreeStatistics.INTER_NODE_DISTANCE).getMean())
        				//Tree structure
        				.put("Cable length", treeStats.getSummaryStats(TreeStatistics.LENGTH).getMean())
        				.put("Complexity index",  treeStats.getSummaryStats(TreeStatistics.COMPLEXITY_INDEX).getMean())
        				.put("Convexhull: Boundary size",  treeStats.getSummaryStats(TreeStatistics.CONVEX_HULL_BOUNDARY_SIZE).getMean())
        				//Spine analysis
        				.put("Number of spines",  treeStats.getSummaryStats(TreeStatistics.N_SPINES).getMean())
        				.put("Number of spines per path",  treeStats.getSummaryStats(TreeStatistics.PATH_N_SPINES).getMean())
        				.put("Path spine density",  treeStats.getSummaryStats(TreeStatistics.PATH_SPINE_DENSITY).getMean())
        				//Sholl analysis
        				.put("Sholl: decay",  treeStats.getSummaryStats(TreeStatistics.SHOLL_DECAY).getMean())
        				.put("Sholl: degree of polynomial fit",  treeStats.getSummaryStats(TreeStatistics.SHOLL_POLY_FIT_DEGREE).getMean())
        				.put("Sholl: Sum", treeStats.getSummaryStats(TreeStatistics.SHOLL_SUM_VALUE).getMean())
        				.put("Sholl: Mean",  treeStats.getSummaryStats(TreeStatistics.SHOLL_MEAN_VALUE).getMean())
        				.put("Sholl: Max" ,  treeStats.getSummaryStats(TreeStatistics.SHOLL_MAX_VALUE).getMean())
        				.put("Sholl: Max (fitted)",  treeStats.getSummaryStats(TreeStatistics.SHOLL_MAX_FITTED).getMean())
        				.put("Sholl: Max (fitted) radius",  treeStats.getSummaryStats(TreeStatistics.SHOLL_MAX_FITTED_RADIUS).getMean())
        				.put("Sholl: number of maxima", treeStats.getSummaryStats(TreeStatistics.SHOLL_N_MAX).getMean())
        				.put("Sholl: Ramification index", treeStats.getSummaryStats(TreeStatistics.SHOLL_RAMIFICATION_INDEX).getMean())
        				.put("Sholl: Skewness", treeStats.getSummaryStats(TreeStatistics.SHOLL_SKEWENESS).getMean())
        				.put("Sholl: Kurtosis",  treeStats.getSummaryStats(TreeStatistics.SHOLL_KURTOSIS).getMean())
        				.put("Soma EDT value (for parameter)", maxEuclidean.getRealFloat())
        				.build()
        		)
        	)
        	
        	//Sholl Analysis        
	        TreeParser parser = new TreeParser(tree);
	        parser.setCenter(centroid);
	        parser.setStepSize(shollStepSize);
	        parser.parse();
	        Profile shollProfile = parser.getProfile();
	        HashMap<String, Double> tempShollMap = listToMap(shollProfile.radii(), shollProfile.counts());  
	        runningShollTable.add(tempShollMap);
	        Table shollTable = Tables.wrap(shollProfile.counts(), (thisRegionLabel+1) + "-Counts", shollProfile.radii().stream().map(String::valueOf).collect(Collectors.toList()));
	        ioService.save(shollTable, thisRegionOutputFolder + (thisRegionLabel+1) + "-ShollCounts.csv");  
        }
        	            
        isolatedImagePlus.close();
        rotating.close();
        thisRegionMask.close();
        thisSkeletonized.close();
    })//End of loop for LabelRegion
    if(runningTable.isEmpty()){
    	println("No suitable microglia found in file " + fileList[i].getName() + ", recommend checking parameter settings.");
    }
    else{
	    concatenatedTable = Tables.wrap(runningTable, treeNames);
		ioService.save(concatenatedTable, (fileList[i].getPath()) + "-out" + File.separator + "concatenatedResultsTable.csv");
		uiService.show(concatenatedTable);
		
		concatenatedShollTable = Tables.wrap(runningShollTable, treeNames);
		ioService.save(concatenatedShollTable, (fileList[i].getPath()) + "-out" + File.separator + "concatenatedShollTable.csv");
		uiService.show(concatenatedShollTable);
    }
}

println("Script Finished!");
