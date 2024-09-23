/**
 * Divides the active image into XY blocks and produces a table of the StDev
 * of a Gaussian curve fit to the autocorrelation function for a given channel.
 *
 * This can give an idea of the resolution for that section of the image, if
 * the image is of a suitably fine-detailed structure (like actin cytoskeleton)
 * that is not too dense.
 *
 * Requires Fiji and the Colocalization by Cross-Correlation plugin
 *
 * @author Andrew McCall
 */

import net.imagej.DatasetService
import net.imagej.axis.Axes;
import net.imagej.Dataset;
import net.imagej.ops.OpService
import net.imglib2.FinalInterval;
import org.scijava.module.ModuleInfo
import org.scijava.module.ModuleService;
import org.scijava.table.Table;
import org.scijava.table.Tables
import org.scijava.ui.UIService;

#@ int (label="Autocorrelation channel:") corrCh
#@ int (label="XY Blocksize") blocksize
#@ Dataset inputDataset
#@ UIService uiService
#@ OpService ops
#@ ModuleService moduleService
#@ DatasetService datasetService

--corrCh;
ModuleInfo ccc = moduleService.getModuleById("command:CCC.Colocalization_by_Cross_Correlation");

long[] min = new long[inputDataset.numDimensions()];
long[] max = new long[inputDataset.numDimensions()];
for(int d = 0; d < min.length; ++d){
    min[d] = inputDataset.min(d);
    max[d] = inputDataset.max(d);
}

int channelAxis = inputDataset.dimensionIndex(Axes.CHANNEL);
int xAxis = inputDataset.dimensionIndex(Axes.X);
int yAxis = inputDataset.dimensionIndex(Axes.Y);

Dataset resTestImage;
if(channelAxis != -1){

    min[channelAxis] = corrCh;
    max[channelAxis] = corrCh;

}

ArrayList<String> listHeaders = new ArrayList<>();
ArrayList<HashMap<String, Double>> correlations = new ArrayList<>();

for (long x = 0; x < inputDataset.dimension(Axes.X); x += blocksize){
    min[xAxis] = x;
    max[xAxis] = x + blocksize - 1;
    if(max[xAxis] >= inputDataset.dimension(Axes.X))
        max[xAxis] = inputDataset.dimension(Axes.X)-1
    if(min[xAxis] == max[xAxis])
        continue;

    listHeaders.add("" + x);
    HashMap<String, Double> rowHashMap = new LinkedHashMap<>();
    for (long y = 0; y < inputDataset.dimension(Axes.Y); y += blocksize){
        min[yAxis] = y;
        max[yAxis] = y + blocksize - 1;
        if(max[yAxis] >= inputDataset.dimension(Axes.Y))
            max[yAxis] = inputDataset.dimension(Axes.Y)-1
        if(min[yAxis] == max[yAxis])
            continue;

        FinalInterval testInterval = new FinalInterval(min, max);
        resTestImage = datasetService.create(ops.transform().crop(inputDataset, testInterval , true));

        Double autocorrelationStDev = moduleService.run(ccc, false,
                "dataset1", resTestImage,
                "dataset2", resTestImage,
                "maskAbsent", true,
                "maskDataset", resTestImage,
                "significantDigits", 4,
                "generateContributionImages",false,
                "showIntermediates",false,
                "saveFolder", new File("")
        ).get().getOutput("resultsTable").get(0,1);

        rowHashMap.put("" + y, autocorrelationStDev);
    }
    correlations.add(rowHashMap);
}

Table stDevTable = Tables.wrap(correlations, listHeaders);
uiService.show(stDevTable);
