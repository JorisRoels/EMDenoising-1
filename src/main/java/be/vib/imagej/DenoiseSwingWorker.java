package be.vib.imagej;

import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import be.vib.bits.QExecutor;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

class DenoiseSwingWorker extends SwingWorker<ImagePlus, Integer>
{
	private Denoiser denoiser;
	private ImagePlus noisyImagePlus;
	private ImageRange range;
	private String algorithmName;
	private JProgressBar progressBar;
	private Runnable whenDone;  // Will be run on the EDT as soon as the DenoiseSwingWorker is done denoising. Can be used to indicate in the UI that we are done.
	
	public DenoiseSwingWorker(Denoiser denoiser, ImagePlus noisyImagePlus, ImageRange range, String algorithmName, JProgressBar progressBar, Runnable whenDone)
	{
		this.denoiser = denoiser;
		this.noisyImagePlus = noisyImagePlus;
		this.range = range;
		this.algorithmName = algorithmName;
		this.progressBar = progressBar;
		this.whenDone = whenDone;
	}
	
	@Override
	public ImagePlus doInBackground() throws InterruptedException, ExecutionException  // TODO: check what happens with exception - should we handle it ourselves here?
	{
		// doInBackground is run is a thread different from the Java Event Dispatch Thread (EDT). Do not update Java Swing components here.
		final int width = noisyImagePlus.getWidth();
		final int height = noisyImagePlus.getHeight();
		
		final ImageStack noisyStack = noisyImagePlus.getStack();
				
		ImageStack denoisedStack = new ImageStack(width, height);
		
		for (int slice = range.getFirst(); slice <= range.getLast(); slice++)
		{
			ImageProcessor imageProcessor = noisyStack.getProcessor(slice);
			LinearImage image = new LinearImage(width, height, WizardModel.getPixelsCopy(imageProcessor));
			
			denoiser.setImage(image);
			LinearImage denoisedResult = QExecutor.getInstance().submit(denoiser).get(); // TODO: check what happens to quasar::exception_t if thrown from C++ during the denoiser task.
			
			ByteProcessor denoisedImage = new ByteProcessor(denoisedResult.width, denoisedResult.height, denoisedResult.pixels);
			denoisedStack.addSlice("", denoisedImage);
			
			publish(slice);
		}
		
		String title = noisyImagePlus.getTitle() + " ["+ algorithmName + "]";
		ImagePlus denoisedImagePlus = new ImagePlus(title, denoisedStack);
		return denoisedImagePlus;
	}
	
	@Override
	protected void process(List<Integer> chunks)  // executed on the Java EDT, so we can update the UI here
	{
		for (Integer slice : chunks)
		{
			progressBar.setValue(slice);
		}
	}
	
	@Override
	public void done()  // executed on the Java EDT, we can update the UI here.
	{
		try
		{
			whenDone.run();
			
			ImagePlus denoisedImagePlus = get();
			denoisedImagePlus.show();			
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
