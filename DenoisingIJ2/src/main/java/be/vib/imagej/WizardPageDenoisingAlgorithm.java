package be.vib.imagej;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;

import be.vib.bits.QExecutor;
import be.vib.bits.QFunction;
import be.vib.bits.QHost;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

public class WizardPageDenoisingAlgorithm extends WizardPage 
{
	private JPanel algoParamsPanel;
	private CardLayout algoParamsCardLayout;
	
	private NumberFormat integerFormat;
	private NumberFormat floatFormat;
	
	private ImagePanel origImagePanel;
	private ImagePanel denoisedImagePanel;
	
    static final int maxPreviewSize = 256;
    
	public WizardPageDenoisingAlgorithm(WizardModel model, String name)
	{
		super(model, name);
		setupFormatters();			
		buildUI();		
	}

	private void setupFormatters()
	{
		integerFormat = NumberFormat.getNumberInstance();
		
		floatFormat = NumberFormat.getNumberInstance();
		floatFormat.setMinimumFractionDigits(2);
	}
	
	private void buildUI()
	{
	    JRadioButton gaussianButton = new JRadioButton("Gaussian");
	    gaussianButton.setSelected(model.denoisingAlgorithm == WizardModel.DenoisingAlgorithm.GAUSSIAN);
	    	    
	    JRadioButton diffusionButton = new JRadioButton("Anisotropic Diffusion");
	    diffusionButton.setSelected(model.denoisingAlgorithm == WizardModel.DenoisingAlgorithm.ANISOTROPIC_DIFFUSION);
	    
		JRadioButton nlmeansButton = new JRadioButton("Non-local means");
		nlmeansButton.setSelected(model.denoisingAlgorithm == WizardModel.DenoisingAlgorithm.NLMS);

	    JRadioButton waveletButton = new JRadioButton("Wavelet Thresholding");
	    waveletButton.setSelected(model.denoisingAlgorithm == WizardModel.DenoisingAlgorithm.WAVELET_THRESHOLDING);
	    	    
	    nlmeansButton.addActionListener(e -> {
    		WizardModel.DenoisingAlgorithm algorithm = WizardModel.DenoisingAlgorithm.NLMS;
    		algoParamsCardLayout.show(algoParamsPanel, algorithm.name());
			model.denoisingAlgorithm = algorithm;
			recalculateDenoisedPreview();
	    });

	    diffusionButton.addActionListener(e -> {
    		WizardModel.DenoisingAlgorithm algorithm = WizardModel.DenoisingAlgorithm.ANISOTROPIC_DIFFUSION;
    		algoParamsCardLayout.show(algoParamsPanel, algorithm.name());
			model.denoisingAlgorithm = algorithm;
			recalculateDenoisedPreview();
    	});

	    gaussianButton.addActionListener(e -> {
    		WizardModel.DenoisingAlgorithm algorithm = WizardModel.DenoisingAlgorithm.GAUSSIAN;
    		algoParamsCardLayout.show(algoParamsPanel, algorithm.name());
			model.denoisingAlgorithm = algorithm;
			recalculateDenoisedPreview();
    	});
	    
	    waveletButton.addActionListener(e -> {
    		WizardModel.DenoisingAlgorithm algorithm = WizardModel.DenoisingAlgorithm.WAVELET_THRESHOLDING;
    		algoParamsCardLayout.show(algoParamsPanel, algorithm.name());
			model.denoisingAlgorithm = algorithm;
			recalculateDenoisedPreview();
    	});
	    
	    // Add radio buttons to group so they are mutually exclusive
	    ButtonGroup group = new ButtonGroup();
	    group.add(nlmeansButton);
	    group.add(diffusionButton);
	    group.add(gaussianButton);
	    group.add(waveletButton);
				
		JPanel algoChoicePanel = new JPanel();
		algoChoicePanel.setLayout(new BoxLayout(algoChoicePanel, BoxLayout.Y_AXIS));
		algoChoicePanel.setBorder(BorderFactory.createTitledBorder("Denoising Algorithm"));
		algoChoicePanel.add(gaussianButton);
		algoChoicePanel.add(diffusionButton);
		algoChoicePanel.add(nlmeansButton);
		algoChoicePanel.add(waveletButton);
		algoChoicePanel.add(Box.createVerticalGlue());
		
		algoParamsCardLayout = new CardLayout();
		algoParamsPanel = new JPanel(algoParamsCardLayout);
		algoParamsPanel.add(new NonlocalMeansParamsPanel(), WizardModel.DenoisingAlgorithm.NLMS.name());
		algoParamsPanel.add(new AnisotropicDiffusionParamsPanel(), WizardModel.DenoisingAlgorithm.ANISOTROPIC_DIFFUSION.name());
		algoParamsPanel.add(new GaussianParamsPanel(), WizardModel.DenoisingAlgorithm.GAUSSIAN.name());
		algoParamsPanel.add(new WaveletThresholdingParamsPanel(), WizardModel.DenoisingAlgorithm.WAVELET_THRESHOLDING.name());
		algoParamsCardLayout.show(algoParamsPanel, model.denoisingAlgorithm.name());
		
		JPanel algorithmPanel = new JPanel();
		algorithmPanel.setLayout(new BoxLayout(algorithmPanel, BoxLayout.X_AXIS));
		algorithmPanel.add(algoChoicePanel);
		algorithmPanel.add(algoParamsPanel);
		
		JPanel previewPanel = new PreviewPanel();
		
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));		
		add(previewPanel);
		add(algorithmPanel);
	}
	
	private class NonlocalMeansParamsPanel extends JPanel  // FIXME: extract this class (and similar ones) - needs a little refactoring though because it depends on enclosing class
	{
		private JSpinner searchWindowSpinner;
		private JSlider searchWindowSlider;
		
		private JSpinner halfBlockSizeSpinner;
		private JSlider halfBlockSizeSlider;
		
		private JSlider sigmaSlider;
		
		public NonlocalMeansParamsPanel()
		{
			setBorder(BorderFactory.createTitledBorder("Non-Local Means Denoising Parameters"));
			
			final float sigmaMin = NonLocalMeansParams.sigmaMin;
			final float sigmaMax = NonLocalMeansParams.sigmaMax;
			
			Function<Float, Integer> toSlider = sigma -> (int)(100 * (sigma - sigmaMin) / (sigmaMax - sigmaMin));
			Function<Integer, Float> fromSlider = s -> sigmaMin + (sigmaMax - sigmaMin) * s / 100.0f;

			JLabel sigmaLabel = new JLabel("Sigma:");
			JFormattedTextField sigmaField = new JFormattedTextField(floatFormat);
			sigmaField.setValue(new Float(model.nonLocalMeansParams.sigma));
			sigmaField.setColumns(5);
			sigmaField.addPropertyChangeListener("value", e -> {
				float newValue = ((Number)sigmaField.getValue()).floatValue();
				if (newValue == model.nonLocalMeansParams.sigma) return;
				model.nonLocalMeansParams.sigma = newValue;
				System.out.println("textfield updated model, sigma now:" + model.nonLocalMeansParams.sigma);
				sigmaSlider.setValue(toSlider.apply(model.nonLocalMeansParams.sigma));
				recalculateDenoisedPreview();
			});
			
			sigmaSlider = new JSlider(0, 100, toSlider.apply(model.nonLocalMeansParams.sigma));
			sigmaSlider.addChangeListener(e -> {
		    	int newValue = ((Number)sigmaSlider.getValue()).intValue();
		    	float newSigma = fromSlider.apply(newValue);
		    	if (newSigma == model.nonLocalMeansParams.sigma) return;
		    	model.nonLocalMeansParams.sigma = newSigma;
				System.out.println("slider updated model, sigma now:" + model.nonLocalMeansParams.sigma);
				sigmaField.setValue(new Float(model.nonLocalMeansParams.sigma));
				recalculateDenoisedPreview();
		    });

			final int searchWindowMin = NonLocalMeansParams.searchWindowMin;
			final int searchWindowMax = NonLocalMeansParams.searchWindowMax;
			
			JLabel searchWindowLabel = new JLabel("Search window:");
			
			SpinnerModel searchWindowSpinnerModel = new SpinnerNumberModel(model.nonLocalMeansParams.searchWindow, searchWindowMin, searchWindowMax, 1);
			searchWindowSpinner = new JSpinner(searchWindowSpinnerModel);
			searchWindowSpinner.addChangeListener(e -> {
		    	int newValue = ((Number)searchWindowSpinner.getValue()).intValue();
		    	if (newValue == model.nonLocalMeansParams.searchWindow) return;
		    	model.nonLocalMeansParams.searchWindow = newValue;
				System.out.println("spinner updated model, searchWindow now:" + model.nonLocalMeansParams.searchWindow);
				searchWindowSlider.setValue(newValue);
				recalculateDenoisedPreview();
		    });
	
			searchWindowSlider = new JSlider(searchWindowMin, searchWindowMax, model.nonLocalMeansParams.searchWindow);		
			searchWindowSlider.addChangeListener(e -> {
		    	int newValue = ((Number)searchWindowSlider.getValue()).intValue();
		    	if (newValue == model.nonLocalMeansParams.searchWindow) return;
		    	model.nonLocalMeansParams.searchWindow = newValue;
				System.out.println("slider updated model, searchWindow now:" + model.nonLocalMeansParams.searchWindow);
				searchWindowSpinner.setValue(newValue);
				recalculateDenoisedPreview();
		    });
			
			JLabel halfBlockSizeLabel = new JLabel("Half block size:");
	
			final int halfBlockSizeMin = NonLocalMeansParams.halfBlockSizeMin;
			final int halfBlockSizeMax = NonLocalMeansParams.halfBlockSizeMax;
	
			SpinnerModel halfBlockSizeSpinnerModel = new SpinnerNumberModel(model.nonLocalMeansParams.halfBlockSize, halfBlockSizeMin, halfBlockSizeMax, 1);
			halfBlockSizeSpinner = new JSpinner(halfBlockSizeSpinnerModel);
			halfBlockSizeSpinner.addChangeListener(e -> {
		    	int newValue = ((Number)halfBlockSizeSpinner.getValue()).intValue();
		    	if (newValue == model.nonLocalMeansParams.halfBlockSize) return;
		    	model.nonLocalMeansParams.halfBlockSize = newValue;
				System.out.println("spinner updated model, halfBlockSize now:" + model.nonLocalMeansParams.halfBlockSize);
				halfBlockSizeSlider.setValue(newValue);
				recalculateDenoisedPreview();
		    });
			
			halfBlockSizeSlider = new JSlider(halfBlockSizeMin, halfBlockSizeMax, model.nonLocalMeansParams.halfBlockSize);
			halfBlockSizeSlider.addChangeListener(e -> {
		    	int newValue = ((Number)halfBlockSizeSlider.getValue()).intValue();
		    	if (newValue == model.nonLocalMeansParams.halfBlockSize) return;
		    	model.nonLocalMeansParams.halfBlockSize = newValue;
				System.out.println("slider updated model, halfBlockSize now:" + model.nonLocalMeansParams.halfBlockSize);
				halfBlockSizeSpinner.setValue(newValue);
				recalculateDenoisedPreview();
		    });
			
			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateGaps(true);
			layout.setAutoCreateContainerGaps(true);
			
			layout.setHorizontalGroup(
			   layout.createSequentialGroup()
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING, false)
				           .addComponent(sigmaLabel)
				           .addComponent(searchWindowLabel)
			      		   .addComponent(halfBlockSizeLabel))
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
				           .addComponent(sigmaField)
				           .addComponent(searchWindowSpinner)
			      		   .addComponent(halfBlockSizeSpinner))
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
			    		   .addComponent(sigmaSlider)
			               .addComponent(searchWindowSlider)
			               .addComponent(halfBlockSizeSlider))
			);
			
			layout.setVerticalGroup(
			   layout.createSequentialGroup()
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
			    		  .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
			    				  .addComponent(sigmaLabel)
			    				  .addComponent(sigmaField))
				           .addComponent(sigmaSlider))
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
			    		  .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
			    				  .addComponent(searchWindowLabel)
			    				  .addComponent(searchWindowSpinner))
				           .addComponent(searchWindowSlider))
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
			    		  .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
			    		  		.addComponent(halfBlockSizeLabel)
			    		  		.addComponent(halfBlockSizeSpinner))
			    		  .addComponent(halfBlockSizeSlider))
			);		
			
			setLayout(layout);
		}
	}
	
	private class AnisotropicDiffusionParamsPanel extends JPanel
	{
		public AnisotropicDiffusionParamsPanel()
		{
			setBorder(BorderFactory.createTitledBorder("Anisotropic Diffusion Denoising Parameters"));
			
			JLabel diffusionFactorLabel = new JLabel("Diffusion factor:");
			JFormattedTextField diffusionFactorField = new JFormattedTextField(floatFormat);
			diffusionFactorField.setColumns(5);
			diffusionFactorField.setValue(new Float(model.anisotropicDiffusionParams.diffusionFactor));
			diffusionFactorField.addPropertyChangeListener("value", e -> { model.anisotropicDiffusionParams.diffusionFactor = ((Number)diffusionFactorField.getValue()).floatValue();
	                                                                       System.out.println("model updated, diffusion factor now:" + model.anisotropicDiffusionParams.diffusionFactor);
	                                                                       recalculateDenoisedPreview(); });
			
			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateGaps(true);
			layout.setAutoCreateContainerGaps(true);
					
			layout.setHorizontalGroup(
			   layout.createSequentialGroup()
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
				           .addComponent(diffusionFactorLabel))
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
				           .addComponent(diffusionFactorField))
			);
			
			layout.setVerticalGroup(
			   layout.createSequentialGroup()
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
				           .addComponent(diffusionFactorLabel)
				           .addComponent(diffusionFactorField))
			);		
			
			setLayout(layout);
		}
	}
	
	private class GaussianParamsPanel extends JPanel
	{
		public GaussianParamsPanel()
		{
			setBorder(BorderFactory.createTitledBorder("Gaussian Denoising Parameters"));
						
			JLabel sigmaLabel = new JLabel("Sigma:");
			JFormattedTextField sigmaField = new JFormattedTextField(floatFormat);
			sigmaField.setColumns(5);
			sigmaField.setValue(new Float(model.gaussianParams.sigma));
			sigmaField.addPropertyChangeListener("value", e -> { model.gaussianParams.sigma = ((Number)sigmaField.getValue()).floatValue();
	                                                             System.out.println("model updated, sigma now:" + model.gaussianParams.sigma);
	                                                             recalculateDenoisedPreview(); });
			
			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateGaps(true);
			layout.setAutoCreateContainerGaps(true);
					
			layout.setHorizontalGroup(
			   layout.createSequentialGroup()
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
				           .addComponent(sigmaLabel))
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
				           .addComponent(sigmaField))
			);
			
			layout.setVerticalGroup(
			   layout.createSequentialGroup()
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
				           .addComponent(sigmaLabel)
				           .addComponent(sigmaField))
			);		
			
			setLayout(layout);
		}
	}
	
	private class WaveletThresholdingParamsPanel extends JPanel
	{		
		public WaveletThresholdingParamsPanel()
		{			
			setBorder(BorderFactory.createTitledBorder("Wavelet Thresholding Parameters"));
						
			JLabel alphaLabel = new JLabel("Alpha:");
			JFormattedTextField alphaField = new JFormattedTextField(floatFormat);
			alphaField.setColumns(5);
			alphaField.setValue(new Float(model.gaussianParams.sigma));
			alphaField.addPropertyChangeListener("value", e -> { model.waveletThresholdingParams.alpha = ((Number)alphaField.getValue()).floatValue();
	                                                             System.out.println("model updated, alpha now:" + model.waveletThresholdingParams.alpha);
	                                                             recalculateDenoisedPreview(); });
			
			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateGaps(true);
			layout.setAutoCreateContainerGaps(true);
					
			layout.setHorizontalGroup(
			   layout.createSequentialGroup()
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
				           .addComponent(alphaLabel))
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
				           .addComponent(alphaField))
			);
			
			layout.setVerticalGroup(
			   layout.createSequentialGroup()
			      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
				           .addComponent(alphaLabel)
				           .addComponent(alphaField))
			);		
			
			setLayout(layout);
		}
	}
	
	private class PreviewPanel extends JPanel
	{
		public PreviewPanel()
		{
			setBorder(BorderFactory.createTitledBorder("Denoising Preview"));
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			
			origImagePanel = new ImagePanel();
			denoisedImagePanel = new ImagePanel();
			
			JPanel origImagePane = addTitle(origImagePanel, "Original ROI");
			JPanel denoisedImagePane = addTitle(denoisedImagePanel, "Denoised ROI");
			
			add(Box.createHorizontalGlue());
			add(origImagePane);
			add(Box.createRigidArea(new Dimension(20,0)));
			add(denoisedImagePane);
			add(Box.createHorizontalGlue());
		}
	}
	
	private void recalculateOrigPreview()
	{
		model.previewOrigROI = cropImage(model.imagePlus, model.roi);
		origImagePanel.setImage(model.previewOrigROI.getBufferedImage(), maxPreviewSize);
	}
	
	private byte[] getPixelsCopy(ImageProcessor image)
	{
		Object pixelsObject = image.duplicate().getPixels();
		assert(pixelsObject instanceof byte[]);
		return (byte[])pixelsObject; 		
	}
	
	private Denoiser newDenoiser()
	{
		LinearImage image = new LinearImage(model.previewOrigROI.getWidth(), model.previewOrigROI.getHeight(), getPixelsCopy(model.previewOrigROI));
		
		switch (model.denoisingAlgorithm)
		{
			case NLMS:
				return new NonLocalMeansDenoiser(image, model.nonLocalMeansParams);
			case GAUSSIAN:
				return new GaussianDenoiser(image, model.gaussianParams);
			case WAVELET_THRESHOLDING:
				return new WaveletThresholdingDenoiser(image, model.waveletThresholdingParams);
			case ANISOTROPIC_DIFFUSION:
				return new AnisotropicDiffusionDenoiser(image, model.anisotropicDiffusionParams);
			default:
				return new NoOpDenoiser(image);
		}
	}

	private void recalculateDenoisedPreview()
	{		
		System.out.println("recalculateDenoisedPreview (Java thread: " + Thread.currentThread().getId() + ")");
		
		DenoiseSwingWorker worker = new DenoiseSwingWorker(newDenoiser(), model, denoisedImagePanel);
		
		// Run the denoising preview on a separate worker thread and return here immediately.
		// Once denoising has completed, the worker will automatically update the denoising
		// preview image in the Java Event Dispatch Thread (EDT).
		worker.execute();

	}
	
	private static JPanel addTitle(JPanel p, String title)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		
		JLabel titleLabel = new JLabel(title);
		titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		
		titleLabel.setAlignmentX(CENTER_ALIGNMENT);
		p.setAlignmentX(CENTER_ALIGNMENT);
		
		panel.add(titleLabel);
		panel.add(p);
		panel.add(Box.createVerticalStrut(10)); // title label introduces some space at the top, also leave some space at the bottom for symmetry
		
		return panel;
	}
	
	@Override
	public void aboutToShowPanel()
	{
		// ROI may have changed, update the previews
		recalculateOrigPreview();
		recalculateDenoisedPreview();
	}
	
	private ImageProcessor cropImage(ImagePlus image, Rectangle roi)
	{
		int slice = model.imagePlus.getCurrentSlice();
		ImageStack stack = image.getStack();
		ImageProcessor imp = stack.getProcessor(slice);
		
		if (model.roi != null)
		{
			imp.setRoi(model.roi);
			return imp.crop();
		}
		else
		{
			return imp.duplicate();
		}
	}
}
